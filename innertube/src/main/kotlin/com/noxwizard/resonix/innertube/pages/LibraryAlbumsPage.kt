package com.noxwizard.resonix.innertube.pages

import com.noxwizard.resonix.innertube.models.Album
import com.noxwizard.resonix.innertube.models.AlbumItem
import com.noxwizard.resonix.innertube.models.Artist
import com.noxwizard.resonix.innertube.models.ArtistItem
import com.noxwizard.resonix.innertube.models.MusicResponsiveListItemRenderer
import com.noxwizard.resonix.innertube.models.MusicTwoRowItemRenderer
import com.noxwizard.resonix.innertube.models.PlaylistItem
import com.noxwizard.resonix.innertube.models.SongItem
import com.noxwizard.resonix.innertube.models.YTItem
import com.noxwizard.resonix.innertube.models.oddElements
import com.noxwizard.resonix.innertube.utils.parseTime

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            return AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = null,
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null
                    )
        }
    }
}

