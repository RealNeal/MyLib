package com.rn.library

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.rn.library.ui.LanguageState
import com.rn.library.update.UpdateNotifier

import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

class MyLibraryApp : Application(), ImageLoaderFactory {
    companion object {
        var instance: MyLibraryApp? = null
            private set
        var currentActivity: Activity? = null
            private set
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.toString()
                val referer = when {
                    url.contains("imglib") || url.contains("cdnlibs") || url.contains("lib.social") || url.contains("mangalib") -> {
                        when {
                            url.contains("ranobelib") || url.contains("ranobe") -> "https://ranobelib.me/"
                            url.contains("animelib") || url.contains("anime") -> "https://animelib.me/"
                            else -> "https://mangalib.me/"
                        }
                    }
                    url.contains("mangabuff") -> "https://mangabuff.ru/"
                    url.contains("anixmirai") || url.contains("anixart") -> "https://anixart.tv/"
                    url.contains("remanga") -> "https://remanga.org/"
                    url.contains("shikimori") -> "https://shikimori.one/"
                    url.contains("wtr-lab") -> "https://wtr-lab.com/"
                    url.contains("ranobehub") -> "https://ranobehub.org/"
                    url.contains("royalroad") -> "https://www.royalroad.com/"
                    url.contains("ranobes") -> "https://ranobes.net/"
                    url.contains("scribblehub") -> "https://www.scribblehub.com/"
                    url.contains("rulate") || url.contains("tl.rulate.ru") -> "https://tl.rulate.ru/"
                    url.contains("media-amazon.com") || url.contains("imdb.com") -> "https://www.imdb.com/"
                    else -> null
                }
                val builder = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                if (referer != null) {
                    builder.header("Referer", referer)
                }
                chain.proceed(builder.build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        LanguageState.applyStoredLanguage(this)
        UpdateNotifier.ensureChannel(this)
        com.rn.library.data.GenreTagManager.init(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }
            override fun onActivityPaused(activity: Activity) {
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }
        })
    }
}
