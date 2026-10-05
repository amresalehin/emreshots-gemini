package com.amresalehin.emreshots.service.ai
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlin.math.sqrt
object VisualFeatureExtractor{
 fun extract(path:String,bins:Int=8):FloatArray?{val b=BitmapFactory.decodeFile(path)?:return null;val s=Bitmap.createScaledBitmap(b,16,16,true);val h=FloatArray(bins*3);val p=IntArray(256);s.getPixels(p,0,16,0,0,16,16);p.forEach{c->h[Color.red(c)*bins/256]++;h[bins+Color.green(c)*bins/256]++;h[2*bins+Color.blue(c)*bins/256]++};val n=sqrt(h.sumOf{it.toDouble()*it}).toFloat().coerceAtLeast(1f);b.recycle();s.recycle();return FloatArray(h.size){h[it]/n}}
 fun cosineSimilarity(a:FloatArray,b:FloatArray):Float{require(a.size==b.size);var d=0f;var x=0f;var y=0f;for(i in a.indices){d+=a[i]*b[i];x+=a[i]*a[i];y+=b[i]*b[i]};return if(x==0f||y==0f)0f else d/(sqrt(x)*sqrt(y))}
}