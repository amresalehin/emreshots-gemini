package com.amresalehin.emreshots.service.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceVisionCatalogTest {
 @Test fun basicDevicePrefersSmallVerifiedModel(){
  val c=DeviceCapabilities(4096,2048,64000,20000,4,35,"arm64-v8a",false,true,true,DevicePerformanceTier.STANDARD)
  val r=OnDeviceVisionCatalog.recommend(c)
  assertNotNull(r.recommended)
  assertEquals("smolvlm-256m-q4",r.recommended!!.id)
 }
 @Test fun lowRamBlocksLargeModels(){
  val c=DeviceCapabilities(2048,800,64000,20000,4,35,"arm64-v8a",true,true,true,DevicePerformanceTier.BASIC)
  val r=OnDeviceVisionCatalog.recommend(c)
  assertTrue(r.blocked.any{it.first.id=="gemma-3-4b-q4"})
  assertTrue(r.recommended!!.minRamMb < 3000)
 }
 @Test fun unsupportedAbiHasNoRecommendation(){
  val c=DeviceCapabilities(8192,4096,64000,20000,8,35,"armeabi-v7a",false,true,true,DevicePerformanceTier.POWERFUL)
  val r=OnDeviceVisionCatalog.recommend(c)
  assertEquals(null,r.recommended)
 }
}