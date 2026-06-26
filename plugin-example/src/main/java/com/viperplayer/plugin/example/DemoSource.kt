package com.viperplayer.plugin.example

import com.viperplayer.plugin.model.Album
import com.viperplayer.plugin.model.Artist
import com.viperplayer.plugin.model.Artwork
import com.viperplayer.plugin.model.AudioFormat
import com.viperplayer.plugin.model.BrowseCategory
import com.viperplayer.plugin.model.CategoryContentType
import com.viperplayer.plugin.model.HomeContent
import com.viperplayer.plugin.model.HomeSection
import com.viperplayer.plugin.model.MediaItem
import com.viperplayer.plugin.model.MediaType
import com.viperplayer.plugin.model.Page
import com.viperplayer.plugin.model.PageRequest
import com.viperplayer.plugin.model.PcmEncoding
import com.viperplayer.plugin.model.Playlist
import com.viperplayer.plugin.model.PluginErrorCode
import com.viperplayer.plugin.model.PluginException
import com.viperplayer.plugin.model.SearchRequest
import com.viperplayer.plugin.model.SearchResult
import com.viperplayer.plugin.model.SearchSuggestions
import com.viperplayer.plugin.model.Song
import com.viperplayer.plugin.author.AudioStreamWriter
import com.viperplayer.plugin.author.SourceProvider
import com.viperplayer.plugin.author.StreamResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A [SourceProvider] backed by a fixed, in-memory catalog of synthwave music that streams PCM
 * silence for playback. This is the canonical reference implementation: every method maps a typed
 * request onto the demo data, and [resolveStream] shows the [AudioStreamWriter] pattern end to end.
 */
class DemoSource : SourceProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeStreams = mutableMapOf<String, Job>()

    // ==================== Search ====================

    override suspend fun search(request: SearchRequest): SearchResult {
        delay(200) // simulate network latency
        val query = request.query.lowercase()
        val types = request.types // empty = every type
        val limit = request.page.limit

        val items = buildList<MediaItem> {
            if (types.isEmpty() || MediaType.SONG in types) {
                addAll(
                    DEMO_SONGS.filter {
                        it.title.lowercase().contains(query) ||
                            it.artists.any { artist -> artist.name.lowercase().contains(query) }
                    }.take(limit)
                )
            }
            if (types.isEmpty() || MediaType.ALBUM in types) {
                addAll(
                    DEMO_ALBUMS.filter {
                        it.name.lowercase().contains(query) ||
                            it.artists.any { artist -> artist.name.lowercase().contains(query) }
                    }.take(limit)
                )
            }
            if (types.isEmpty() || MediaType.ARTIST in types) {
                addAll(DEMO_ARTISTS.filter { it.name.lowercase().contains(query) }.take(limit))
            }
            if (types.isEmpty() || MediaType.PLAYLIST in types) {
                addAll(DEMO_PLAYLISTS.filter { it.name.lowercase().contains(query) }.take(limit))
            }
        }
        return SearchResult(items = items.take(limit))
    }

    override suspend fun getSearchSuggestions(query: String): SearchSuggestions {
        delay(100)
        val q = query.lowercase()
        val songs = DEMO_SONGS.filter {
            it.title.lowercase().contains(q) ||
                it.artists.any { artist -> artist.name.lowercase().contains(q) }
        }.take(5)
        val artists = DEMO_ARTISTS.filter { it.name.lowercase().contains(q) }.take(5)
        return SearchSuggestions(items = (songs + artists).take(10))
    }

    // ==================== Details ====================

    override suspend fun getSong(id: String): Song {
        delay(50)
        return DEMO_SONGS.find { it.id == id }
            ?: throw PluginException(PluginErrorCode.NOT_FOUND, "Song not found: $id")
    }

    override suspend fun getAlbum(id: String): Album {
        delay(50)
        val album = DEMO_ALBUMS.find { it.id == id }
            ?: throw PluginException(PluginErrorCode.NOT_FOUND, "Album not found: $id")
        // Populate the album's tracks on a detail fetch.
        return album.copy(songs = DEMO_SONGS.filter { it.album?.id == album.id })
    }

    override suspend fun getArtist(id: String): Artist {
        delay(50)
        return DEMO_ARTISTS.find { it.id == id }
            ?: throw PluginException(PluginErrorCode.NOT_FOUND, "Artist not found: $id")
    }

    override suspend fun getPlaylist(id: String): Playlist {
        delay(50)
        val playlist = DEMO_PLAYLISTS.find { it.id == id }
            ?: throw PluginException(PluginErrorCode.NOT_FOUND, "Playlist not found: $id")
        // Populate the playlist's tracks on a detail fetch.
        return playlist.copy(songs = PLAYLIST_SONGS[playlist.id].orEmpty())
    }

    // ==================== Library ====================

    override suspend fun getLibrarySongs(page: PageRequest): Page<Song> {
        delay(100)
        return Page(items = DEMO_SONGS.take(page.limit))
    }

    override suspend fun getLibraryAlbums(page: PageRequest): Page<Album> {
        delay(100)
        return Page(items = DEMO_ALBUMS.take(page.limit))
    }

    override suspend fun getLibraryArtists(page: PageRequest): Page<Artist> {
        delay(100)
        return Page(items = DEMO_ARTISTS.take(page.limit))
    }

    override suspend fun getLibraryPlaylists(page: PageRequest): Page<Playlist> {
        delay(100)
        return Page(items = DEMO_PLAYLISTS.take(page.limit))
    }

    // ==================== Browse ====================

    override suspend fun getBrowseCategories(page: PageRequest): Page<BrowseCategory> {
        delay(100)
        return Page(items = DEMO_CATEGORIES.take(page.limit))
    }

    override suspend fun getCategoryContents(categoryId: String, page: PageRequest): SearchResult {
        delay(150)
        val limit = page.limit
        val items: List<MediaItem> = when (categoryId) {
            "new-releases" -> DEMO_ALBUMS.filter { (it.releaseYear ?: 0) >= 2020 }.take(limit)
            "top-songs" -> DEMO_SONGS.sortedByDescending { it.durationMs }.take(limit)
            "featured-playlists" -> DEMO_PLAYLISTS.take(limit)
            "genres-synthwave" -> DEMO_SONGS.filter { "Synthwave" in it.genres }.take(limit)
            "genres-electronic" -> DEMO_ALBUMS.take(limit)
            "artists" -> DEMO_ARTISTS.take(limit)
            "recently-played" -> DEMO_SONGS.shuffled().take(limit)
            else -> emptyList()
        }
        return SearchResult(items = items)
    }

    // ==================== Home ====================

    override suspend fun getHome(): HomeContent {
        delay(100)
        return HomeContent(
            quickPicks = DEMO_SONGS.shuffled().take(5),
            sections = listOf(
                HomeSection(
                    id = "made_for_you",
                    title = "Made For You",
                    items = DEMO_PLAYLISTS.take(3),
                ),
                HomeSection(
                    id = "new_releases",
                    title = "New Releases",
                    items = DEMO_ALBUMS.filter { (it.releaseYear ?: 0) >= 2020 }.take(5),
                ),
            ),
        )
    }

    // ==================== Streaming ====================

    override suspend fun resolveStream(songId: String, type: MediaType): StreamResponse {
        val song = getSong(songId)
        val writer = AudioStreamWriter.create(
            format = AudioFormat(sampleRate = 44100, channelCount = 2, encoding = PcmEncoding.PCM_16BIT),
            durationMs = song.durationMs,
            seekable = true,
        )
        activeStreams[writer.streamId] = scope.launch {
            try {
                streamSilence(writer, song.durationMs ?: 0L)
            } finally {
                writer.close()
            }
        }
        return StreamResponse.pcm(writer)
    }

    override suspend fun seekStream(streamId: String, positionMs: Long): Boolean {
        // A real plugin would seek its decoder; the demo just acknowledges the request.
        return true
    }

    override suspend fun closeStream(streamId: String) {
        activeStreams.remove(streamId)?.cancel()
    }

    /** Cancels every in-flight stream and tears down the scope. Called from the service's onShutdown. */
    fun shutdown() {
        activeStreams.values.forEach { it.cancel() }
        activeStreams.clear()
        scope.cancel()
    }

    /** Writes [durationMs] of PCM silence to [writer] in real time, one 100 ms buffer at a time. */
    private suspend fun streamSilence(writer: AudioStreamWriter, durationMs: Long) {
        val format = writer.format
        val bytesPerSecond = format.sampleRate * format.channelCount * 2 // 16-bit = 2 bytes/sample
        val bufferMs = 100L
        val buffer = ByteArray((bytesPerSecond * bufferMs / 1000).toInt()) // all zeros = silence

        var streamedMs = 0L
        while (streamedMs < durationMs && !writer.isClosed) {
            writer.write(buffer)
            streamedMs += bufferMs
            delay(bufferMs) // pace the writes to real time
        }
    }

    companion object {

        private val DEMO_ARTISTS = listOf(
            Artist(
                id = "artist-1",
                name = "The Midnight",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/artist1/300/300",
                    fullUrl = "https://picsum.photos/seed/artist1/300/300",
                ),
            ),
            Artist(
                id = "artist-2",
                name = "FM-84",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/artist2/300/300",
                    fullUrl = "https://picsum.photos/seed/artist2/300/300",
                ),
            ),
            Artist(
                id = "artist-3",
                name = "Gunship",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/artist3/300/300",
                    fullUrl = "https://picsum.photos/seed/artist3/300/300",
                ),
            ),
            Artist(
                id = "artist-4",
                name = "Timecop1983",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/artist4/300/300",
                    fullUrl = "https://picsum.photos/seed/artist4/300/300",
                ),
            ),
            Artist(
                id = "artist-5",
                name = "Carpenter Brut",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/artist5/300/300",
                    fullUrl = "https://picsum.photos/seed/artist5/300/300",
                ),
            ),
            Artist(
                id = "artist-6",
                name = "Perturbator",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/artist6/300/300",
                    fullUrl = "https://picsum.photos/seed/artist6/300/300",
                ),
            ),
            Artist(
                id = "artist-7",
                name = "Kavinsky",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/artist7/300/300",
                    fullUrl = "https://picsum.photos/seed/artist7/300/300",
                ),
            ),
        )

        private val DEMO_ALBUMS = listOf(
            Album(
                id = "album-1",
                name = "Nocturnal",
                artists = listOf(DEMO_ARTISTS[0]),
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/album1/500/500",
                    fullUrl = "https://picsum.photos/seed/album1/500/500",
                ),
                releaseYear = 2017,
                trackCount = 12,
            ),
            Album(
                id = "album-2",
                name = "Atlas",
                artists = listOf(DEMO_ARTISTS[1]),
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/album2/500/500",
                    fullUrl = "https://picsum.photos/seed/album2/500/500",
                ),
                releaseYear = 2016,
                trackCount = 10,
            ),
            Album(
                id = "album-3",
                name = "Dark All Day",
                artists = listOf(DEMO_ARTISTS[2]),
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/album3/500/500",
                    fullUrl = "https://picsum.photos/seed/album3/500/500",
                ),
                releaseYear = 2018,
                trackCount = 14,
            ),
            Album(
                id = "album-4",
                name = "Kids",
                artists = listOf(DEMO_ARTISTS[0]),
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/album4/500/500",
                    fullUrl = "https://picsum.photos/seed/album4/500/500",
                ),
                releaseYear = 2018,
                trackCount = 11,
            ),
            Album(
                id = "album-5",
                name = "Leather Teeth",
                artists = listOf(DEMO_ARTISTS[4]),
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/album5/500/500",
                    fullUrl = "https://picsum.photos/seed/album5/500/500",
                ),
                releaseYear = 2018,
                trackCount = 9,
            ),
            Album(
                id = "album-6",
                name = "Dangerous Days",
                artists = listOf(DEMO_ARTISTS[5]),
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/album6/500/500",
                    fullUrl = "https://picsum.photos/seed/album6/500/500",
                ),
                releaseYear = 2014,
                trackCount = 13,
            ),
            Album(
                id = "album-7",
                name = "OutRun",
                artists = listOf(DEMO_ARTISTS[6]),
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/album7/500/500",
                    fullUrl = "https://picsum.photos/seed/album7/500/500",
                ),
                releaseYear = 2013,
                trackCount = 10,
            ),
            Album(
                id = "album-8",
                name = "Reflections",
                artists = listOf(DEMO_ARTISTS[3]),
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/album8/500/500",
                    fullUrl = "https://picsum.photos/seed/album8/500/500",
                ),
                releaseYear = 2020,
                trackCount = 12,
            ),
            Album(
                id = "album-9",
                name = "Monsters",
                artists = listOf(DEMO_ARTISTS[0]),
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/album9/500/500",
                    fullUrl = "https://picsum.photos/seed/album9/500/500",
                ),
                releaseYear = 2020,
                trackCount = 15,
            ),
            Album(
                id = "album-10",
                name = "New Model",
                artists = listOf(DEMO_ARTISTS[4]),
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/album10/500/500",
                    fullUrl = "https://picsum.photos/seed/album10/500/500",
                ),
                releaseYear = 2021,
                trackCount = 11,
            ),
        )

        private val DEMO_SONGS = listOf(
            // The Midnight - Nocturnal
            Song(id = "song-1", title = "Sunset", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[0], durationMs = 245000L, trackNumber = 1, genres = listOf("Synthwave")),
            Song(id = "song-2", title = "Los Angeles", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[0], durationMs = 312000L, trackNumber = 2, genres = listOf("Synthwave")),
            Song(id = "song-3", title = "Crystalline", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[0], durationMs = 278000L, trackNumber = 3, genres = listOf("Synthwave")),
            Song(id = "song-4", title = "River of Darkness", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[0], durationMs = 301000L, trackNumber = 4, genres = listOf("Synthwave")),
            // The Midnight - Kids
            Song(id = "song-5", title = "Kids", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[3], durationMs = 267000L, trackNumber = 1, genres = listOf("Synthwave")),
            Song(id = "song-6", title = "Lost Boy", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[3], durationMs = 293000L, trackNumber = 2, genres = listOf("Synthwave")),
            Song(id = "song-7", title = "America 2", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[3], durationMs = 256000L, trackNumber = 3, genres = listOf("Synthwave")),
            // The Midnight - Monsters
            Song(id = "song-8", title = "Deep Blue", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[8], durationMs = 289000L, trackNumber = 1, genres = listOf("Synthwave")),
            Song(id = "song-9", title = "Monsters", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[8], durationMs = 312000L, trackNumber = 2, genres = listOf("Synthwave")),
            // FM-84 - Atlas
            Song(id = "song-10", title = "Running in the Night", artists = listOf(DEMO_ARTISTS[1]), album = DEMO_ALBUMS[1], durationMs = 289000L, trackNumber = 1, genres = listOf("Synthwave", "Retrowave")),
            Song(id = "song-11", title = "Every Road", artists = listOf(DEMO_ARTISTS[1]), album = DEMO_ALBUMS[1], durationMs = 276000L, trackNumber = 2, genres = listOf("Synthwave", "Retrowave")),
            Song(id = "song-12", title = "Wild Ones", artists = listOf(DEMO_ARTISTS[1]), album = DEMO_ALBUMS[1], durationMs = 301000L, trackNumber = 3, genres = listOf("Synthwave", "Retrowave")),
            // Gunship - Dark All Day
            Song(id = "song-13", title = "Dark All Day", artists = listOf(DEMO_ARTISTS[2]), album = DEMO_ALBUMS[2], durationMs = 298000L, trackNumber = 1, genres = listOf("Darksynth")),
            Song(id = "song-14", title = "When You Grow Up", artists = listOf(DEMO_ARTISTS[2]), album = DEMO_ALBUMS[2], durationMs = 324000L, trackNumber = 2, genres = listOf("Darksynth")),
            Song(id = "song-15", title = "Art3mis & Parzival", artists = listOf(DEMO_ARTISTS[2]), album = DEMO_ALBUMS[2], durationMs = 267000L, trackNumber = 3, genres = listOf("Darksynth")),
            // Timecop1983 - Reflections
            Song(id = "song-16", title = "On the Run", artists = listOf(DEMO_ARTISTS[3]), album = DEMO_ALBUMS[7], durationMs = 245000L, trackNumber = 1, genres = listOf("Synthwave", "Retrowave")),
            Song(id = "song-17", title = "Reflections", artists = listOf(DEMO_ARTISTS[3]), album = DEMO_ALBUMS[7], durationMs = 278000L, trackNumber = 2, genres = listOf("Synthwave", "Retrowave")),
            Song(id = "song-18", title = "Back to You", artists = listOf(DEMO_ARTISTS[3]), album = DEMO_ALBUMS[7], durationMs = 256000L, trackNumber = 3, genres = listOf("Synthwave", "Retrowave")),
            // Carpenter Brut - Leather Teeth
            Song(id = "song-19", title = "Leather Teeth", artists = listOf(DEMO_ARTISTS[4]), album = DEMO_ALBUMS[4], durationMs = 312000L, trackNumber = 1, genres = listOf("Darksynth")),
            Song(id = "song-20", title = "Cheerleader Effect", artists = listOf(DEMO_ARTISTS[4]), album = DEMO_ALBUMS[4], durationMs = 289000L, trackNumber = 2, genres = listOf("Darksynth")),
            Song(id = "song-21", title = "Beware the Beast", artists = listOf(DEMO_ARTISTS[4]), album = DEMO_ALBUMS[4], durationMs = 301000L, trackNumber = 3, genres = listOf("Darksynth")),
            // Carpenter Brut - New Model
            Song(id = "song-22", title = "Fab Tool", artists = listOf(DEMO_ARTISTS[4]), album = DEMO_ALBUMS[9], durationMs = 267000L, trackNumber = 1, genres = listOf("Darksynth")),
            Song(id = "song-23", title = "The Widow Maker", artists = listOf(DEMO_ARTISTS[4]), album = DEMO_ALBUMS[9], durationMs = 293000L, trackNumber = 2, genres = listOf("Darksynth")),
            // Perturbator - Dangerous Days
            Song(id = "song-24", title = "Future Club", artists = listOf(DEMO_ARTISTS[5]), album = DEMO_ALBUMS[5], durationMs = 312000L, trackNumber = 1, genres = listOf("Darksynth", "Electronic")),
            Song(id = "song-25", title = "Dangerous Days", artists = listOf(DEMO_ARTISTS[5]), album = DEMO_ALBUMS[5], durationMs = 301000L, trackNumber = 2, genres = listOf("Darksynth", "Electronic")),
            Song(id = "song-26", title = "Complete Domination", artists = listOf(DEMO_ARTISTS[5]), album = DEMO_ALBUMS[5], durationMs = 278000L, trackNumber = 3, genres = listOf("Darksynth", "Electronic")),
            // Kavinsky - OutRun
            Song(id = "song-27", title = "Nightcall", artists = listOf(DEMO_ARTISTS[6]), album = DEMO_ALBUMS[6], durationMs = 245000L, trackNumber = 1, genres = listOf("Synthwave", "Retrowave")),
            Song(id = "song-28", title = "Roadgame", artists = listOf(DEMO_ARTISTS[6]), album = DEMO_ALBUMS[6], durationMs = 289000L, trackNumber = 2, genres = listOf("Synthwave", "Retrowave")),
            Song(id = "song-29", title = "Testarossa Autodrive", artists = listOf(DEMO_ARTISTS[6]), album = DEMO_ALBUMS[6], durationMs = 267000L, trackNumber = 3, genres = listOf("Synthwave", "Retrowave")),
            Song(id = "song-30", title = "Odd Look", artists = listOf(DEMO_ARTISTS[6]), album = DEMO_ALBUMS[6], durationMs = 256000L, trackNumber = 4, genres = listOf("Synthwave", "Retrowave")),
        )

        private val DEMO_PLAYLISTS = listOf(
            Playlist(
                id = "playlist-1",
                name = "Synthwave Essentials",
                description = "The best synthwave tracks from all artists",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/playlist1/500/500",
                    fullUrl = "https://picsum.photos/seed/playlist1/500/500",
                ),
                ownerName = "ViPER Player",
                trackCount = 12,
            ),
            Playlist(
                id = "playlist-2",
                name = "Night Drive",
                description = "Perfect for late night driving through the city",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/playlist2/500/500",
                    fullUrl = "https://picsum.photos/seed/playlist2/500/500",
                ),
                ownerName = "ViPER Player",
                trackCount = 10,
            ),
            Playlist(
                id = "playlist-3",
                name = "Darksynth Collection",
                description = "The darkest and heaviest synthwave tracks",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/playlist3/500/500",
                    fullUrl = "https://picsum.photos/seed/playlist3/500/500",
                ),
                ownerName = "ViPER Player",
                trackCount = 8,
            ),
            Playlist(
                id = "playlist-4",
                name = "Retrowave Classics",
                description = "Classic retrowave hits from the golden era",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/playlist4/500/500",
                    fullUrl = "https://picsum.photos/seed/playlist4/500/500",
                ),
                ownerName = "ViPER Player",
                trackCount = 9,
            ),
            Playlist(
                id = "playlist-5",
                name = "The Midnight Mix",
                description = "All the best tracks from The Midnight",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/playlist5/500/500",
                    fullUrl = "https://picsum.photos/seed/playlist5/500/500",
                ),
                ownerName = "ViPER Player",
                trackCount = 6,
            ),
            Playlist(
                id = "playlist-6",
                name = "Workout Energy",
                description = "High-energy synthwave for your workout",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/playlist6/500/500",
                    fullUrl = "https://picsum.photos/seed/playlist6/500/500",
                ),
                ownerName = "ViPER Player",
                trackCount = 7,
            ),
            Playlist(
                id = "playlist-7",
                name = "Chill Synthwave",
                description = "Relaxing synthwave for studying or relaxing",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/playlist7/500/500",
                    fullUrl = "https://picsum.photos/seed/playlist7/500/500",
                ),
                ownerName = "ViPER Player",
                trackCount = 8,
            ),
            Playlist(
                id = "playlist-8",
                name = "2020s New Releases",
                description = "Fresh synthwave releases from 2020 onwards",
                artwork = Artwork(
                    thumbnailUrl = "https://picsum.photos/seed/playlist8/500/500",
                    fullUrl = "https://picsum.photos/seed/playlist8/500/500",
                ),
                ownerName = "ViPER Player",
                trackCount = 5,
            ),
        )

        /** Maps each playlist id to its ordered tracks. */
        private val PLAYLIST_SONGS = mapOf(
            "playlist-1" to listOf(
                DEMO_SONGS[0], DEMO_SONGS[1], DEMO_SONGS[9], DEMO_SONGS[10], DEMO_SONGS[15], DEMO_SONGS[16],
                DEMO_SONGS[26], DEMO_SONGS[27], DEMO_SONGS[28], DEMO_SONGS[3], DEMO_SONGS[11], DEMO_SONGS[17]
            ),
            "playlist-2" to listOf(
                DEMO_SONGS[0], DEMO_SONGS[9], DEMO_SONGS[15], DEMO_SONGS[16], DEMO_SONGS[26], DEMO_SONGS[27],
                DEMO_SONGS[28], DEMO_SONGS[29], DEMO_SONGS[1], DEMO_SONGS[10]
            ),
            "playlist-3" to listOf(
                DEMO_SONGS[12], DEMO_SONGS[13], DEMO_SONGS[18], DEMO_SONGS[19], DEMO_SONGS[20], DEMO_SONGS[23],
                DEMO_SONGS[24], DEMO_SONGS[25]
            ),
            "playlist-4" to listOf(
                DEMO_SONGS[9], DEMO_SONGS[10], DEMO_SONGS[15], DEMO_SONGS[16], DEMO_SONGS[26], DEMO_SONGS[27],
                DEMO_SONGS[28], DEMO_SONGS[29], DEMO_SONGS[11]
            ),
            "playlist-5" to listOf(
                DEMO_SONGS[0], DEMO_SONGS[1], DEMO_SONGS[2], DEMO_SONGS[4], DEMO_SONGS[5], DEMO_SONGS[7]
            ),
            "playlist-6" to listOf(
                DEMO_SONGS[18], DEMO_SONGS[19], DEMO_SONGS[23], DEMO_SONGS[24], DEMO_SONGS[25], DEMO_SONGS[20], DEMO_SONGS[21]
            ),
            "playlist-7" to listOf(
                DEMO_SONGS[0], DEMO_SONGS[2], DEMO_SONGS[4], DEMO_SONGS[5], DEMO_SONGS[15], DEMO_SONGS[16], DEMO_SONGS[17], DEMO_SONGS[26]
            ),
            "playlist-8" to listOf(
                DEMO_SONGS[7], DEMO_SONGS[8], DEMO_SONGS[15], DEMO_SONGS[21], DEMO_SONGS[22]
            )
        )

        private val DEMO_CATEGORIES = listOf(
            BrowseCategory(
                id = "new-releases",
                name = "New Releases",
                description = "Latest releases from 2020 onwards",
                imageUrl = "https://picsum.photos/seed/cat1/400/200",
                contentType = CategoryContentType.ALBUMS,
            ),
            BrowseCategory(
                id = "top-songs",
                name = "Top Songs",
                description = "Most popular tracks",
                imageUrl = "https://picsum.photos/seed/cat2/400/200",
                contentType = CategoryContentType.SONGS,
            ),
            BrowseCategory(
                id = "featured-playlists",
                name = "Featured Playlists",
                description = "Curated playlists for every mood",
                imageUrl = "https://picsum.photos/seed/cat3/400/200",
                contentType = CategoryContentType.PLAYLISTS,
            ),
            BrowseCategory(
                id = "genres-synthwave",
                name = "Synthwave",
                description = "Classic synthwave tracks",
                imageUrl = "https://picsum.photos/seed/cat4/400/200",
                contentType = CategoryContentType.SONGS,
            ),
            BrowseCategory(
                id = "genres-electronic",
                name = "Electronic",
                description = "Electronic music collection",
                imageUrl = "https://picsum.photos/seed/cat5/400/200",
                contentType = CategoryContentType.ALBUMS,
            ),
            BrowseCategory(
                id = "artists",
                name = "Artists",
                description = "Browse by artist",
                imageUrl = "https://picsum.photos/seed/cat6/400/200",
                contentType = CategoryContentType.ARTISTS,
            ),
            BrowseCategory(
                id = "recently-played",
                name = "Recently Played",
                description = "Your recently played tracks",
                imageUrl = "https://picsum.photos/seed/cat7/400/200",
                contentType = CategoryContentType.SONGS,
            ),
        )
    }
}
