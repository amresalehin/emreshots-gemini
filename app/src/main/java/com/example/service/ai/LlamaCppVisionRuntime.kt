package com.amresalehin.emreshots.service.ai
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File
class LlamaCppVisionRuntimeFactory:VisionRuntimeFactory{
 override fun supports(m:OnDeviceVisionModel,c:DeviceCapabilities)=m.runtime==VisionRuntimeBackend.LLAMA_CPP_MTMD&&c.abi in m.supportedAbis&&c.apiLevel>=m.minApiLevel
 override fun create(m:OnDeviceVisionModel,context:Context):VisionModelProvider?=if(supports(m,OnDeviceVisionCapabilityDetector.detect(context)))LlamaCppVisionModelProvider(m,context)else null
}
private class LlamaCppVisionModelProvider(private val model:OnDeviceVisionModel,private val context:Context):VisionModelProvider{
 override val modelId get()=model.id
 override val runtime=VisionRuntimeBackend.LLAMA_CPP_MTMD
 private val events=MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity=64)
 private val scope=kotlinx.coroutines.CoroutineScope(Dispatchers.IO+kotlinx.coroutines.SupervisorJob())
 private val helper=LlamaHelper(context.contentResolver,scope,events)
 private var loaded=false
 override suspend fun load(installed:InstalledVisionModel,quality:VisionQualityPreset){
  val base=installed.model.artifacts.firstOrNull{it.id=="base"}?:error("Missing base model");val mm=installed.model.artifacts.firstOrNull{it.id=="mmproj"}
  helper.load(Uri.fromFile(File(installed.directory,base.fileName)).toString(),when(quality){VisionQualityPreset.FAST->2048;VisionQualityPreset.BALANCED->4096;VisionQualityPreset.DEEP->8192},mm?.let{Uri.fromFile(File(installed.directory,it.fileName)).toString()}){loaded=it>0}
  withTimeout(120000){events.first{e->when(e){is LlamaHelper.LLMEvent.Loaded->true;is LlamaHelper.LLMEvent.Error->error(e.message);else->false}}};check(loaded)
 }
 override suspend fun analyze(r:VisionAnalysisRequest)=withContext(Dispatchers.IO){
  val image=if(r.imagePath.startsWith("content://")||r.imagePath.startsWith("file://"))r.imagePath else Uri.fromFile(File(r.imagePath)).toString()
  helper.predict(prompt(r),image,false)
  when(val e=withTimeout(180000){events.first{it is LlamaHelper.LLMEvent.Done||it is LlamaHelper.LLMEvent.Error}}){
   is LlamaHelper.LLMEvent.Done->parse(e.fullText,r.ocrText)
   is LlamaHelper.LLMEvent.Error->VisionAnalysisResult(false,modelId,errorMessage=e.message)
   else->error("Unexpected runtime event")
  }
 }
 override suspend fun cancel(){helper.stopPrediction()}
 override suspend fun unload(){helper.abort();helper.release();loaded=false}
 private fun prompt(r:VisionAnalysisRequest):String{val o=r.ocrText?.takeIf{it.isNotBlank()}?.let{" Local OCR evidence: "+it}.orEmpty();return "Analyze this screenshot locally. Return ONLY JSON with title, description, tags, category, detectedLinks, visualSummary. "+quality(r.quality)+o}
 private fun quality(q:VisionQualityPreset)=when(q){VisionQualityPreset.FAST->"Be concise.";VisionQualityPreset.BALANCED->"Be accurate and concise.";VisionQualityPreset.DEEP->"Reason carefully about UI structure and visible links."}
 private fun parse(raw:String,ocr:String?):VisionAnalysisResult{val s=raw.indexOf("{");val e=raw.lastIndexOf("}");if(s<0||e<=s)return VisionAnalysisResult(false,modelId,rawText=raw,ocrTextUsed=ocr,errorMessage="Local model did not return JSON.");return try{val j=JSONObject(raw.substring(s,e+1));val tags=j.optJSONArray("tags")?.let{a->buildList{for(i in 0 until a.length())a.optString(i).takeIf{it.isNotBlank()}?.let(::add)}}.orEmpty();val links=j.optJSONArray("detectedLinks")?.let{a->buildList{for(i in 0 until a.length())a.optString(i).takeIf{it.isNotBlank()}?.let(::add)}}.orEmpty();VisionAnalysisResult(true,modelId,j.optString("title"),j.optString("description").ifBlank{j.optString("visualSummary")},tags,j.optString("category").takeIf{it.isNotBlank()&&it!="null"},links,ocrTextUsed=ocr,rawText=raw)}catch(t:Throwable){VisionAnalysisResult(false,modelId,rawText=raw,ocrTextUsed=ocr,errorMessage=t.message)}}
}