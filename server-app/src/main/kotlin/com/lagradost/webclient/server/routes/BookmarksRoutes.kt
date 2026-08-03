package com.lagradost.webclient.server.routes

import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.webclient.api.BookmarkDto
import com.lagradost.webclient.api.BookmarksResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put

private fun bookmarkId(provider: String, url: String) = "${provider}_${url.hashCode()}"

fun Route.bookmarksRoutes() {
    get("/api/bookmarks") {
        call.respond(BookmarksResponse(DesktopDataStore.getBookmarks().map { it.toDto() }))
    }

    put("/api/bookmarks") {
        val bookmark = call.receive<BookmarkDto>()
        DesktopDataStore.addBookmark(
            DesktopBookmark(
                id = bookmarkId(bookmark.provider, bookmark.url),
                name = bookmark.name,
                url = bookmark.url,
                apiName = bookmark.provider,
                posterUrl = bookmark.posterUrl,
            ),
        )
        call.respond(HttpStatusCode.OK)
    }

    delete("/api/bookmarks") {
        val provider = call.request.queryParameters["provider"]
        val url = call.request.queryParameters["url"]
        if (provider == null || url == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@delete
        }
        DesktopDataStore.removeBookmark(bookmarkId(provider, url))
        call.respond(HttpStatusCode.OK)
    }
}
