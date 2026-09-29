package org.mlm.mages.platform

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer

private class WebBlobFetcher(
    private val path: String,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = retrieveWebBlob(path) ?: return null
        return SourceFetchResult(
            source = ImageSource(Buffer().apply { write(bytes) }, options.fileSystem),
            mimeType = retrieveWebBlobMimeType(path),
            dataSource = DataSource.MEMORY,
        )
    }
}

private class WebBlobFetcherFactory : Fetcher.Factory<Uri> {
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
        val path = webBlobKey(data.toString()) ?: return null
        return WebBlobFetcher(path, options)
    }
}

fun installWebImageLoader() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(WebBlobFetcherFactory()) }
            .build()
    }
}
