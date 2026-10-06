package com.amresalehin.emreshots.service.ai
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
class OnDeviceVisionModelManager(context:Context,private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(5,TimeUnit.MINUTES).build()){
 private val root=File(context.filesDir,"models/ondevice-vision").apply{mkdirs()}
 suspend fun installedModels()=withContext(Dispatchers.IO){OnDeviceVisionCatalog.all().mapNotNull{installedModel(it)}}
 suspend fun installedModel(m:OnDeviceVisionModel):InstalledVisionModel?=withContext(Dispatchers.IO){if(m.artifacts.isEmpty())return@withContext null;val d=File(root,m.id);if(!d.isDirectory)return@withContext null;if(!m.artifacts.all{a->val f=File(d,a.fileName);f.isFile&&f.length()==a.sizeBytes&&a.sha256!="UNVERIFIED"&&sha256(f)==a.sha256})return@withContext null;InstalledVisionModel(m,d,d.walkTopDown().filter{it.isFile}.sumOf{it.length()})}
 suspend fun isInstalled(m:OnDeviceVisionModel)=installedModel(m)!=null
 suspend fun install(m:OnDeviceVisionModel,onProgress:(Long,Long)->Unit={_,_->})=withContext(Dispatchers.IO){
  require(m.artifacts.isNotEmpty()){"No verified downloadable bundle is available for "+m.displayName};require(m.artifacts.none{it.sha256=="UNVERIFIED"}){"Checksum is not pinned for this model."}
  val d=File(root,m.id).apply{mkdirs()};val total=m.artifacts.sumOf{it.sizeBytes};var done=0L;onProgress(0,total)
  m.artifacts.forEach{a->val target=File(d,a.fileName);if(target.isFile&&target.length()==a.sizeBytes&&sha256(target)==a.sha256){done+=a.sizeBytes;onProgress(done,total);return@forEach};val part=File(d,a.fileName+".part");download(a.url,part,a.sizeBytes){onProgress(done+it,total)};check(sha256(part).equals(a.sha256,true)){"Integrity verification failed for "+a.fileName};if(target.exists())target.delete();check(part.renameTo(target));done+=a.sizeBytes;onProgress(done,total)}
  check(installedModel(m)!=null){"Model bundle verification failed after installation."}
 }
 suspend fun delete(m:OnDeviceVisionModel)=withContext(Dispatchers.IO){File(root,m.id).deleteRecursively()}
 suspend fun storageUsageBytes()=withContext(Dispatchers.IO){root.walkTopDown().filter{it.isFile}.sumOf{it.length()}}
 private fun download(url:String,p:File,expected:Long,onProgress:(Long)->Unit){
  var offset=if(p.exists())p.length()else 0L
  if(offset>expected){p.delete();offset=0L}
  val request=Request.Builder().url(url).apply{if(offset>0)header("Range","bytes="+offset+"-")}.build()
  client.newCall(request).execute().use{response->
   check(response.isSuccessful){"Download failed: HTTP "+response.code}
   val append=offset>0&&response.code==206
   if(!append){offset=0L;RandomAccessFile(p,"rw").use{it.setLength(0L)}}
   val body=response.body?:error("Empty model response")
   body.byteStream().use{input->
    RandomAccessFile(p,"rw").use{output->
     output.seek(offset);val buffer=ByteArray(65536);var totalRead=offset
     while(true){val read=input.read(buffer);if(read<0)break;output.write(buffer,0,read);totalRead+=read;onProgress(totalRead)}
    }
   }
  }
  check(p.length()>0){"Downloaded empty model artifact"}
 }
 private fun sha256(f:File):String{val d=MessageDigest.getInstance("SHA-256");f.inputStream().use{i->val b=ByteArray(65536);while(true){val n=i.read(b);if(n<0)break;d.update(b,0,n)}};return d.digest().joinToString(""){"%02x".format(it)}}
}