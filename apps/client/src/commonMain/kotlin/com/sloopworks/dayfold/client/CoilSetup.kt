package com.sloopworks.dayfold.client

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade

private var configured = false

/**
 * ADR 0036 — one-time Coil setup. Installs the Ktor network fetcher (resolves the
 * platform ktor engine already on the classpath: cio desktop / okhttp android /
 * darwin iOS) and enables crossfade. Idempotent (SingletonImageLoader.setSafe only
 * applies if unset). Every image URL still passes MediaValidation before Coil sees
 * it — the loader is the transport, not the gate.
 */
fun setupImageLoader() {
  if (configured) return
  configured = true
  SingletonImageLoader.setSafe { ctx: PlatformContext ->
    ImageLoader.Builder(ctx)
      .components { add(KtorNetworkFetcherFactory()) }
      .crossfade(true)
      .build()
  }
}
