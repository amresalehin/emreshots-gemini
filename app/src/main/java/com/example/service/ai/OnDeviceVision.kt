package com.amresalehin.emreshots.service.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

enum class OnDeviceVisionMode { AUTOMATIC, FORCE_LOCAL, DISABLED;
    companion object { fun fromPreference(v:String)=runCatching{valueOf(v.trim().uppercase(Locale.US).replace("-","_").replace(" ","_"))}.getOrDefault(AUTOMATIC) } }
enum class VisionQualityPreset { FAST, BALANCED, DEEP }
enum class VisionSelectionMode { AUTOMATIC, FASTEST, BALANCED, HIGHEST_QUALITY, MANUAL }
enum class VisionRuntimeBackend { LLAMA_CPP_MTMD, ONNX_RUNTIME_MOBILE, LITERT_MEDIAPIPE, NNAPI, VENDOR_NPU }
enum class DevicePerformanceTier { BASIC, STANDARD, POWERFUL, HIGH_END }
enum class OnDeviceVisionCapability { TITLE_GENERATION, DESCRIPTION_SUMMARY, TAG_SUGGESTIONS, CATEGORY_TOPIC, LINK_DETECTION, VISUAL_FEATURES, OCR_CONTEXT, VISUAL_QA }

data class DeviceCapabilities(
    val totalRamMb:Long,val availableRamMb:Long,val totalStorageMb:Long,val availableStorageMb:Long,
    val cpuCores:Int,val apiLevel:Int,val abi:String,val isLowRamDevice:Boolean,val hasGpu:Boolean,val hasVulkan:Boolean,
    val performanceTier:DevicePerformanceTier
){ fun canRun(m:OnDeviceVisionModel)=abi in m.supportedAbis && apiLevel>=m.minApiLevel && totalRamMb>=m.minRamMb && availableStorageMb>=m.storageMb+256L }

data class ModelArtifact(val id:String,val fileName:String,val url:String,val sizeBytes:Long,val sha256:String)
data class OnDeviceVisionModel(
    val id:String,val family:String,val displayName:String,val parameterCount:String,val quantization:String,
    val storageMb:Int,val minRamMb:Int,val recommendedRamMb:Int,val minApiLevel:Int=24,
    val supportedAbis:Set<String> = setOf("arm64-v8a","x86_64"),
    val runtime:VisionRuntimeBackend=VisionRuntimeBackend.LLAMA_CPP_MTMD,
    val capabilities:Set<OnDeviceVisionCapability> = OnDeviceVisionCapability.entries.toSet(),
    val acceleratorSupport:String="CPU/NEON",val expectedSpeed:String="Device dependent",val license:String,
    val sourceUrl:String,val artifacts:List<ModelArtifact>,val notes:String=""
){ val totalDownloadBytes get()=artifacts.sumOf{it.sizeBytes} }
data class InstalledVisionModel(val model:OnDeviceVisionModel,val directory:File,val installedBytes:Long)
data class OnDeviceModelRecommendation(val tier:DevicePerformanceTier,val recommended:OnDeviceVisionModel?,val alternatives:List<OnDeviceVisionModel>,val blocked:List<Pair<OnDeviceVisionModel,String>> = emptyList(),val explanation:String="")
data class VisionAnalysisRequest(val imagePath:String,val quality:VisionQualityPreset=VisionQualityPreset.BALANCED,val ocrText:String?=null,val requestedCapabilities:Set<OnDeviceVisionCapability> = OnDeviceVisionCapability.entries.toSet())
data class VisionAnalysisResult(
    val isSuccess:Boolean,val modelId:String,val title:String="",val description:String="",val tags:List<String> = emptyList(),
    val category:String?=null,val detectedLinks:List<String> = emptyList(),val visualFeatures:FloatArray?=null,val ocrTextUsed:String?=null,
    val rawText:String="",val modelUsed:String="",val processingTimeMs:Long=0L,val errorMessage:String?=null
)
interface VisionModelProvider{val modelId:String;val runtime:VisionRuntimeBackend;suspend fun load(model:InstalledVisionModel,quality:VisionQualityPreset);suspend fun analyze(request:VisionAnalysisRequest):VisionAnalysisResult;suspend fun cancel();suspend fun unload()}
interface VisionRuntimeFactory{fun supports(model:OnDeviceVisionModel,capabilities:DeviceCapabilities):Boolean;fun create(model:OnDeviceVisionModel,context:Context):VisionModelProvider?}

object OnDeviceVisionCatalog {
    private const val HF="https://huggingface.co"
    private val models=listOf(
        OnDeviceVisionModel("smolvlm-256m-q4","SmolVLM","SmolVLM 256M Instruct Q4_K_M","0.26B","Q4_K_M",718,1800,2400,
            capabilities=setOf(OnDeviceVisionCapability.TITLE_GENERATION,OnDeviceVisionCapability.DESCRIPTION_SUMMARY,OnDeviceVisionCapability.TAG_SUGGESTIONS,OnDeviceVisionCapability.CATEGORY_TOPIC,OnDeviceVisionCapability.LINK_DETECTION,OnDeviceVisionCapability.VISUAL_QA,OnDeviceVisionCapability.OCR_CONTEXT),
            expectedSpeed="Fastest local VLM profile",license="Apache-2.0",sourceUrl="$HF/pierretokns/SmolVLM-256M-Instruct-GGUF",artifacts=listOf(
                ModelArtifact("base","SmolVLM-256M-Instruct-Q4_K_M.gguf","$HF/pierretokns/SmolVLM-256M-Instruct-GGUF/resolve/main/SmolVLM-256M-Instruct-Q4_K_M.gguf",125_053_120,"8f19fa336b353f60389efcdcfe75ab52e584193266646fe397e0de7903319e57"),
                ModelArtifact("mmproj","mmproj-SmolVLM-256M-Instruct-Q8_0.gguf","$HF/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/mmproj-SmolVLM-256M-Instruct-Q8_0.gguf",103_769_856,"7e943f7c53f0382a6fc41b6ee0c2def63ba4fded9ab8ed039cc9e2ab905e0edd"))),
        OnDeviceVisionModel("smolvlm-256m-q8","SmolVLM","SmolVLM 256M Instruct Q8_0","0.26B","Q8_0",279,1800,6000,
            capabilities=setOf(OnDeviceVisionCapability.TITLE_GENERATION,OnDeviceVisionCapability.DESCRIPTION_SUMMARY,OnDeviceVisionCapability.TAG_SUGGESTIONS,OnDeviceVisionCapability.CATEGORY_TOPIC,OnDeviceVisionCapability.LINK_DETECTION,OnDeviceVisionCapability.VISUAL_QA,OnDeviceVisionCapability.OCR_CONTEXT),
            expectedSpeed="Higher-quality 256M local profile",license="Apache-2.0",sourceUrl="$HF/ggml-org/SmolVLM-256M-Instruct-GGUF",artifacts=listOf(
                ModelArtifact("base","SmolVLM-256M-Instruct-Q8_0.gguf","$HF/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/SmolVLM-256M-Instruct-Q8_0.gguf",175_000_000,"2a31195d3769c0b0fd0a4906201666108834848db768af11de1d2cef7cd35e65"),
                ModelArtifact("mmproj","mmproj-SmolVLM-256M-Instruct-Q8_0.gguf","$HF/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/mmproj-SmolVLM-256M-Instruct-Q8_0.gguf",103_769_856,"7e943f7c53f0382a6fc41b6ee0c2def63ba4fded9ab8ed039cc9e2ab905e0edd"))),
        OnDeviceVisionModel("smolvlm2-256m-video-q4","SmolVLM2","SmolVLM2 256M Video Instruct Q4_K_M","0.2B","Q4_K_M",235,1900,2500,
            capabilities=setOf(OnDeviceVisionCapability.TITLE_GENERATION,OnDeviceVisionCapability.DESCRIPTION_SUMMARY,OnDeviceVisionCapability.TAG_SUGGESTIONS,OnDeviceVisionCapability.CATEGORY_TOPIC,OnDeviceVisionCapability.VISUAL_QA,OnDeviceVisionCapability.OCR_CONTEXT),
            expectedSpeed="Fast",license="Apache-2.0",sourceUrl="$HF/ggml-org/SmolVLM2-256M-Video-Instruct-GGUF",artifacts=listOf(
                ModelArtifact("base","SmolVLM2-256M-Video-Instruct-Q4_K_M.gguf","$HF/ggml-org/SmolVLM2-256M-Video-Instruct-GGUF/resolve/main/SmolVLM2-256M-Video-Instruct-Q4_K_M.gguf",131_000_000,"UNVERIFIED"),
                ModelArtifact("mmproj","mmproj-SmolVLM2-256M-Video-Instruct-Q8_0.gguf","$HF/ggml-org/SmolVLM2-256M-Video-Instruct-GGUF/resolve/main/mmproj-SmolVLM2-256M-Video-Instruct-Q8_0.gguf",104_000_000,"05d5751132244a6ebd64cba9b34898c0d874b2cb78159d758e1d4da3aad91581"))),
        OnDeviceVisionModel("gemma-3-4b-q4","Gemma","Gemma 3 4B Instruct Q4_K_M","4B","Q4_K_M",3341,5200,7200,capabilities=OnDeviceVisionCapability.entries.toSet(),
            acceleratorSupport="CPU/NEON; vendor/GPU adapters can share this provider",expectedSpeed="Balanced on 8 GB+ devices",license="Gemma",sourceUrl="$HF/ggml-org/gemma-3-4b-it-GGUF",artifacts=listOf(
                ModelArtifact("base","gemma-3-4b-it-Q4_K_M.gguf","$HF/ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",2_490_000_000,"882e8d2db44dc554fb0ea5077cb7e4bc49e7342a1f0da57901c0802ea21a0863"),
                ModelArtifact("mmproj","mmproj-model-f16.gguf","$HF/ggml-org/gemma-3-4b-it-GGUF/resolve/main/mmproj-model-f16.gguf",851_000_000,"8c0fb064b019a6972856aaae2c7e4792858af3ca4561be2dbf649123ba6c40cb"))),
        OnDeviceVisionModel("qwen2.5-vl-3b-q4","Qwen2.5-VL","Qwen2.5-VL 3B Instruct Q4_K_M","3B","Q4_K_M + Q8 projector",2775,5000,7000,capabilities=OnDeviceVisionCapability.entries.toSet(),
            expectedSpeed="Balanced / high quality",license="Apache-2.0",sourceUrl="$HF/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF",artifacts=listOf(
                ModelArtifact("base","Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf","$HF/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf",1_930_000_000,"d02fe9b69ad8cadbbd228e387667af66612c44bed29ffc8eb1e7caf9ac486c12"),
                ModelArtifact("mmproj","mmproj-Qwen2.5-VL-3B-Instruct-Q8_0.gguf","$HF/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/mmproj-Qwen2.5-VL-3B-Instruct-Q8_0.gguf",845_000_000,"980c9b2f78c04e6cff93d277ada09e768394f112d75db3b4e9dea8a69f9fb904"))),
        OnDeviceVisionModel("minicpm-v-2.6-q4","MiniCPM-V","MiniCPM-V 2.6 Q4_K_M","8B-class","Q4_K_M",5700,8500,11000,capabilities=OnDeviceVisionCapability.entries.toSet(),
            expectedSpeed="High quality; slower on CPU",license="Model-specific",sourceUrl="$HF/openbmb/MiniCPM-V-2_6-gguf",artifacts=emptyList(),notes="Compatibility profile; verified bundle not pinned yet."),
        OnDeviceVisionModel("mobilevlm-1.7b","MobileVLM","MobileVLM 1.7B","1.7B","4-bit profile",1900,4000,6000,
            capabilities=setOf(OnDeviceVisionCapability.TITLE_GENERATION,OnDeviceVisionCapability.DESCRIPTION_SUMMARY,OnDeviceVisionCapability.TAG_SUGGESTIONS,OnDeviceVisionCapability.CATEGORY_TOPIC,OnDeviceVisionCapability.VISUAL_QA),
            expectedSpeed="Fast on suitable mid-range devices",license="Model-specific",sourceUrl="https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal/MobileVLM.md",artifacts=emptyList(),notes="Compatibility profile; import a verified bundle when available.")
    )
    fun all()=models
    fun find(id:String)=models.firstOrNull{it.id==id}
    fun recommend(c:DeviceCapabilities,mode:VisionSelectionMode=VisionSelectionMode.AUTOMATIC):OnDeviceModelRecommendation{
        val ok=models.filter{c.canRun(it)&&it.artifacts.isNotEmpty()&&it.artifacts.none{a->a.sha256=="UNVERIFIED"}}
        val blocked=models.filterNot{ok.contains(it)}.mapNotNull{m->when{
            c.abi !in m.supportedAbis->"Unsupported CPU architecture ("+c.abi+")"
            c.apiLevel<m.minApiLevel->"Requires Android "+m.minApiLevel+"+"
            c.totalRamMb<m.minRamMb->"Needs at least "+m.minRamMb+" MB RAM"
            c.availableStorageMb<m.storageMb+256L->"Not enough free storage"
            m.artifacts.isEmpty()->"Verified download bundle is not published yet"
            m.artifacts.any{it.sha256=="UNVERIFIED"}->"Artifact checksum is not pinned yet"
            else->null
        }?.let{m to it}}
        if(ok.isEmpty())return OnDeviceModelRecommendation(c.performanceTier,null,emptyList(),blocked,"No verified local vision bundle is safe for this device.")
        val r=when(mode){VisionSelectionMode.FASTEST->ok.minByOrNull{it.minRamMb};VisionSelectionMode.HIGHEST_QUALITY->ok.maxByOrNull{it.minRamMb};VisionSelectionMode.BALANCED,VisionSelectionMode.AUTOMATIC->ok.minByOrNull{abs(it.recommendedRamMb-c.totalRamMb)};VisionSelectionMode.MANUAL->null}
        return OnDeviceModelRecommendation(c.performanceTier,r,ok.filter{it.id!=r?.id}.sortedBy{it.minRamMb},blocked,r?.displayName?.let{it+" fits the "+c.performanceTier.name.lowercase()+" device tier with an OS memory margin."}?:"Manual selection enabled.")
    }
}

object OnDeviceVisionCapabilityDetector{
    fun detect(context:Context):DeviceCapabilities{
        val am=context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi=ActivityManager.MemoryInfo().also{am?.getMemoryInfo(it)}
        val st=StatFs(Environment.getDataDirectory().path);val cores=Runtime.getRuntime().availableProcessors();val abi=Build.SUPPORTED_ABIS.firstOrNull()?:"unknown"
        val tier=when{mi.totalMem>=12L*1024*1024*1024&&cores>=8->DevicePerformanceTier.HIGH_END;mi.totalMem>=8L*1024*1024*1024&&cores>=6->DevicePerformanceTier.POWERFUL;mi.totalMem>=4L*1024*1024*1024&&cores>=4->DevicePerformanceTier.STANDARD;else->DevicePerformanceTier.BASIC}
        return DeviceCapabilities(mi.totalMem/1048576,mi.availMem/1048576,st.totalBytes/1048576,st.availableBytes/1048576,cores,Build.VERSION.SDK_INT,abi,am?.isLowRamDevice?:false,true,Build.VERSION.SDK_INT>=24,tier)
    }
}

class OnDeviceVisionService(private val context:Context,private val runtimeFactory:VisionRuntimeFactory=LlamaCppVisionRuntimeFactory()){
    private val manager=OnDeviceVisionModelManager(context)
    suspend fun deviceCapabilities()=withContext(Dispatchers.Default){OnDeviceVisionCapabilityDetector.detect(context)}
    suspend fun recommendation(mode:VisionSelectionMode=VisionSelectionMode.AUTOMATIC)=withContext(Dispatchers.Default){OnDeviceVisionCatalog.recommend(OnDeviceVisionCapabilityDetector.detect(context),mode)}
    suspend fun installedModels()=manager.installedModels()
    suspend fun download(model:OnDeviceVisionModel,onProgress:(Long,Long)->Unit={_,_->})=manager.install(model,onProgress)
    suspend fun delete(model:OnDeviceVisionModel)=manager.delete(model)
    suspend fun storageUsageBytes()=manager.storageUsageBytes()
    suspend fun isInstalled(model:OnDeviceVisionModel)=manager.isInstalled(model)
    suspend fun analyze(request: VisionAnalysisRequest, modePreference: String, modelPreference: String, selectionMode: VisionSelectionMode = VisionSelectionMode.AUTOMATIC): VisionAnalysisResult =
        analyze(request, OnDeviceVisionMode.fromPreference(modePreference), modelPreference, selectionMode)

    suspend fun analyze(request:VisionAnalysisRequest,mode:OnDeviceVisionMode,modelPreference:String,selectionMode:VisionSelectionMode=VisionSelectionMode.AUTOMATIC):VisionAnalysisResult{
        if(mode==OnDeviceVisionMode.DISABLED)return VisionAnalysisResult(false,modelPreference,errorMessage="On-device vision is disabled.")
        val caps=deviceCapabilities()
        val selected=if(modelPreference.isBlank()||modelPreference=="auto")OnDeviceVisionCatalog.recommend(caps,selectionMode).recommended else OnDeviceVisionCatalog.find(modelPreference)?.takeIf{caps.canRun(it)&&it.artifacts.none{a->a.sha256=="UNVERIFIED"}}
        if(selected==null)return VisionAnalysisResult(false,modelPreference,errorMessage="No compatible verified local vision model is available.")
        val installed=manager.installedModel(selected)?:return VisionAnalysisResult(false,selected.id,errorMessage="Model is not installed. Download it from AI settings.")
        val provider=runtimeFactory.create(selected,context)?:return VisionAnalysisResult(false,selected.id,errorMessage="No compatible local runtime is installed for "+selected.displayName+".")
        return try{val s=SystemClock.elapsedRealtime();provider.load(installed,request.quality);provider.analyze(request).copy(modelId=selected.id,modelUsed=selected.displayName,processingTimeMs=SystemClock.elapsedRealtime()-s)}
        catch(t:Throwable){runCatching{provider.unload()};VisionAnalysisResult(false,selected.id,errorMessage=t.message?:"Local vision inference failed.")}
        finally{runCatching{provider.unload()}}
    }
}
