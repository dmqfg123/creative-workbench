package com.creativeworkbench.writer.core.ai

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局共享一个 OkHttpClient：连接池、线程池、DNS 缓存都复用。
 * 之前每发一次请求就 new 一个 client——每次调用都要新建线程池、丢掉连接复用，
 * 长文写作时连续调用（挑设定 + 续写 + 总结）会明显多花握手时间。
 *
 * 超时策略沿用原来的口径：连接 20s、写 60s；读超时是「空闲超时」不是总时长
 * （与 DSH 的 streamIdleTimeoutMs = 5 分钟一致），写长文时不会被总时长掐断。
 */
object HttpClientProvider {
    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20L, TimeUnit.SECONDS)
            .readTimeout(300L, TimeUnit.SECONDS)
            .writeTimeout(60L, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 检索接口要的是快失败，复用一个带短读超时的克隆（仍然共享连接池）。 */
    val search: OkHttpClient by lazy {
        shared.newBuilder().readTimeout(30L, TimeUnit.SECONDS).build()
    }
}
