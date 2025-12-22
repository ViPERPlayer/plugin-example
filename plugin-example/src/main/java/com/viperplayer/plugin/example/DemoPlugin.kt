package com.viperplayer.plugin.example

import com.viperplayer.plugin.aidl.*
import com.viperplayer.plugin.sdk.*
import kotlinx.coroutines.*

/**
 * Demo plugin that provides fake music data for testing.
 * This demonstrates how to implement a ViperPlugin.
 */
class DemoPlugin : ViperPlugin {
    
    private var host: HostController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeStreams = mutableMapOf<String, Job>()
    
    override val capabilities = PluginCapabilities(
        canSearch = true,
        canBrowse = true,
        hasLibrary = true,
        hasPlaylists = true,
        canSeek = true,
        hasLyrics = false,
        hasHighQuality = false,
        supportsOffline = false,
        hasSettings = false,
        canEditPlaylists = false,
        canSaveToLibrary = false,
        hasRadio = false,
        supportedQualities = listOf(128, 256, 320)
    )
    
    override suspend fun onConnect(host: HostController) {
        this.host = host
    }
    
    override suspend fun onDisconnect() {
        host = null
        activeStreams.values.forEach { it.cancel() }
        activeStreams.clear()
    }
    
    // ==================== Search ====================
    
    override suspend fun search(
        query: String,
        types: Int,
        cursor: String?,
        limit: Int
    ): SearchResult {
        // Simulate network delay
        delay(200)
        
        val lowercaseQuery = query.lowercase()
        
        val songs = if (types and SearchResult.TYPE_SONG != 0) {
            DEMO_SONGS.filter { 
                it.title.lowercase().contains(lowercaseQuery) ||
                it.artistName.lowercase().contains(lowercaseQuery)
            }.take(limit)
        } else emptyList()
        
        val albums = if (types and SearchResult.TYPE_ALBUM != 0) {
            DEMO_ALBUMS.filter {
                it.name.lowercase().contains(lowercaseQuery) ||
                it.artistName.lowercase().contains(lowercaseQuery)
            }.take(limit)
        } else emptyList()
        
        val artists = if (types and SearchResult.TYPE_ARTIST != 0) {
            DEMO_ARTISTS.filter {
                it.name.lowercase().contains(lowercaseQuery)
            }.take(limit)
        } else emptyList()
        
        val playlists = if (types and SearchResult.TYPE_PLAYLIST != 0) {
            DEMO_PLAYLISTS.filter {
                it.name.lowercase().contains(lowercaseQuery)
            }.take(limit)
        } else emptyList()
        
        return SearchResult(
            songs = songs,
            albums = albums,
            artists = artists,
            playlists = playlists,
            nextCursor = null // No pagination in demo
        )
    }
    
    // ==================== Browse ====================
    
    override suspend fun getBrowseCategories(
        cursor: String?,
        limit: Int
    ): PagedResult<BrowseCategory> {
        delay(100)
        return PagedResult(DEMO_CATEGORIES)
    }
    
    override suspend fun getCategoryContents(
        categoryId: String,
        cursor: String?,
        limit: Int
    ): SearchResult {
        delay(150)
        
        return when (categoryId) {
            "new-releases" -> SearchResult(albums = DEMO_ALBUMS.filter { it.releaseYear != null && it.releaseYear!! >= 2020 }.take(limit))
            "top-songs" -> SearchResult(songs = DEMO_SONGS.sortedByDescending { it.durationMs }.take(limit))
            "featured-playlists" -> SearchResult(playlists = DEMO_PLAYLISTS.filter { it.isPublic }.take(limit))
            "genres-synthwave" -> SearchResult(songs = DEMO_SONGS.filter { it.artists.any { artist -> artist.genres.contains("Synthwave") } }.take(limit))
            "genres-electronic" -> SearchResult(albums = DEMO_ALBUMS.filter { it.artists.any { artist -> artist.genres.contains("Electronic") } }.take(limit))
            "artists" -> SearchResult(artists = DEMO_ARTISTS.take(limit))
            "recently-played" -> SearchResult(songs = DEMO_SONGS.shuffled().take(limit))
            else -> SearchResult()
        }
    }
    
    // ==================== Library ====================
    
    override suspend fun getLibrarySongs(
        cursor: String?,
        limit: Int
    ): PagedResult<Song> {
        delay(100)
        return PagedResult(DEMO_SONGS.take(limit))
    }
    
    override suspend fun getLibraryAlbums(
        cursor: String?,
        limit: Int
    ): PagedResult<Album> {
        delay(100)
        return PagedResult(DEMO_ALBUMS.take(limit))
    }
    
    override suspend fun getLibraryArtists(
        cursor: String?,
        limit: Int
    ): PagedResult<Artist> {
        delay(100)
        return PagedResult(DEMO_ARTISTS.take(limit))
    }
    
    override suspend fun getLibraryPlaylists(
        cursor: String?,
        limit: Int
    ): PagedResult<Playlist> {
        delay(100)
        return PagedResult(DEMO_PLAYLISTS.take(limit))
    }
    
    // ==================== Details ====================
    
    override suspend fun getSong(mediaId: MediaId): Song {
        delay(50)
        return DEMO_SONGS.find { it.id == mediaId }
            ?: throw NotFoundException("Song not found: $mediaId")
    }
    
    override suspend fun getAlbum(mediaId: MediaId): Album {
        delay(50)
        val album = DEMO_ALBUMS.find { it.id == mediaId }
            ?: throw NotFoundException("Album not found: $mediaId")
        
        // Return album with tracks populated
        val tracks = DEMO_SONGS.filter { it.album?.id == album.id }
        return album.copy(songs = tracks)
    }
    
    override suspend fun getArtist(mediaId: MediaId): Artist {
        delay(50)
        return DEMO_ARTISTS.find { it.id == mediaId }
            ?: throw NotFoundException("Artist not found: $mediaId")
    }
    
    override suspend fun getArtistSongs(
        artistId: MediaId,
        cursor: String?,
        limit: Int
    ): PagedResult<Song> {
        delay(100)
        val songs = DEMO_SONGS.filter { song ->
            song.artists.any { it.id == artistId }
        }.take(limit)
        return PagedResult(songs)
    }
    
    override suspend fun getArtistAlbums(
        artistId: MediaId,
        cursor: String?,
        limit: Int
    ): PagedResult<Album> {
        delay(100)
        val albums = DEMO_ALBUMS.filter { album ->
            album.artists.any { it.id == artistId }
        }.take(limit)
        return PagedResult(albums)
    }
    
    override suspend fun getPlaylist(mediaId: MediaId): Playlist {
        delay(50)
        val playlist = DEMO_PLAYLISTS.find { it.id == mediaId }
            ?: throw NotFoundException("Playlist not found: $mediaId")
        
        // Get songs for this specific playlist
        val playlistSongs = PLAYLIST_SONGS[playlist.id.sourceId] ?: emptyList()
        return playlist.copy(songs = playlistSongs)
    }
    
    override suspend fun getPlaylistSongs(
        playlistId: MediaId,
        cursor: String?,
        limit: Int
    ): PagedResult<Song> {
        delay(100)
        val playlist = DEMO_PLAYLISTS.find { it.id == playlistId }
            ?: throw NotFoundException("Playlist not found: $playlistId")
        
        // Get songs for this specific playlist
        val playlistSongs = PLAYLIST_SONGS[playlistId.sourceId] ?: emptyList()
        val paginatedSongs = playlistSongs.drop(cursor?.toIntOrNull() ?: 0).take(limit)
        val nextCursor = if (paginatedSongs.size < playlistSongs.size - (cursor?.toIntOrNull() ?: 0)) {
            ((cursor?.toIntOrNull() ?: 0) + limit).toString()
        } else null
        
        return PagedResult(paginatedSongs, nextCursor)
    }
    
    // ==================== Audio Streaming ====================
    
    override suspend fun getAudioStream(mediaId: MediaId): AudioStreamWriter {
        val song = getSong(mediaId)
        
        val writer = AudioStreamWriter.create(
            mediaId = mediaId,
            format = AudioFormat.CD_QUALITY,
            durationMs = song.durationMs,
            canSeek = true
        )
        
        // Start streaming in background
        val job = scope.launch {
            try {
                streamSilence(writer, song.durationMs)
            } catch (e: Exception) {
                // Stream ended or was cancelled
            } finally {
                writer.close()
            }
        }
        
        activeStreams[writer.streamId] = job
        
        return writer
    }
    
    override suspend fun stopAudioStream(streamId: String) {
        activeStreams.remove(streamId)?.cancel()
    }
    
    override suspend fun seekAudioStream(streamId: String, positionMs: Long): Boolean {
        // In a real plugin, you would seek within the audio decoder
        // For demo purposes, we just return true
        return true
    }
    
    /**
     * Generate silence (or could be a sine wave tone for testing).
     */
    private suspend fun streamSilence(writer: AudioStreamWriter, durationMs: Long) {
        val format = writer.format
        val bytesPerSecond = format.bytesPerSecond
        val bufferSizeMs = 100L // 100ms buffers
        val bufferSize = (bytesPerSecond * bufferSizeMs / 1000).toInt()
        val buffer = ByteArray(bufferSize) // All zeros = silence
        
        var streamedMs = 0L
        while (streamedMs < durationMs && !writer.isClosed) {
            writer.write(buffer)
            streamedMs += bufferSizeMs
            
            // Simulate real-time streaming
            delay(bufferSizeMs)
        }
    }
    
    companion object {
        const val PLUGIN_ID = "com.viperplayer.plugin.example"
        
        // Demo data
        private val DEMO_ARTISTS = listOf(
            Artist(
                id = MediaId(PLUGIN_ID, "artist-1"),
                name = "The Midnight",
                imageUrl = "https://picsum.photos/seed/artist1/300/300",
                genres = listOf("Synthwave", "Electronic", "Retrowave"),
                followerCount = 500000
            ),
            Artist(
                id = MediaId(PLUGIN_ID, "artist-2"),
                name = "FM-84",
                imageUrl = "https://picsum.photos/seed/artist2/300/300",
                genres = listOf("Synthwave", "Retrowave", "Pop"),
                followerCount = 250000
            ),
            Artist(
                id = MediaId(PLUGIN_ID, "artist-3"),
                name = "Gunship",
                imageUrl = "https://picsum.photos/seed/artist3/300/300",
                genres = listOf("Synthwave", "Darksynth", "Electronic"),
                followerCount = 350000
            ),
            Artist(
                id = MediaId(PLUGIN_ID, "artist-4"),
                name = "Timecop1983",
                imageUrl = "https://picsum.photos/seed/artist4/300/300",
                genres = listOf("Synthwave", "Retrowave", "Ambient"),
                followerCount = 180000
            ),
            Artist(
                id = MediaId(PLUGIN_ID, "artist-5"),
                name = "Carpenter Brut",
                imageUrl = "https://picsum.photos/seed/artist5/300/300",
                genres = listOf("Darksynth", "Synthwave", "Electronic"),
                followerCount = 420000
            ),
            Artist(
                id = MediaId(PLUGIN_ID, "artist-6"),
                name = "Perturbator",
                imageUrl = "https://picsum.photos/seed/artist6/300/300",
                genres = listOf("Darksynth", "Electronic", "Industrial"),
                followerCount = 380000
            ),
            Artist(
                id = MediaId(PLUGIN_ID, "artist-7"),
                name = "Kavinsky",
                imageUrl = "https://picsum.photos/seed/artist7/300/300",
                genres = listOf("Synthwave", "Electronic", "Retrowave"),
                followerCount = 600000
            )
        )
        
        private val DEMO_ALBUMS = listOf(
            Album(
                id = MediaId(PLUGIN_ID, "album-1"),
                name = "Nocturnal",
                artists = listOf(DEMO_ARTISTS[0]),
                artworkUrl = "https://picsum.photos/seed/album1/500/500",
                releaseYear = 2017,
                trackCount = 12,
                type = AlbumType.ALBUM
            ),
            Album(
                id = MediaId(PLUGIN_ID, "album-2"),
                name = "Atlas",
                artists = listOf(DEMO_ARTISTS[1]),
                artworkUrl = "https://picsum.photos/seed/album2/500/500",
                releaseYear = 2016,
                trackCount = 10,
                type = AlbumType.ALBUM
            ),
            Album(
                id = MediaId(PLUGIN_ID, "album-3"),
                name = "Dark All Day",
                artists = listOf(DEMO_ARTISTS[2]),
                artworkUrl = "https://picsum.photos/seed/album3/500/500",
                releaseYear = 2018,
                trackCount = 14,
                type = AlbumType.ALBUM
            ),
            Album(
                id = MediaId(PLUGIN_ID, "album-4"),
                name = "Kids",
                artists = listOf(DEMO_ARTISTS[0]),
                artworkUrl = "https://picsum.photos/seed/album4/500/500",
                releaseYear = 2018,
                trackCount = 11,
                type = AlbumType.ALBUM
            ),
            Album(
                id = MediaId(PLUGIN_ID, "album-5"),
                name = "Leather Teeth",
                artists = listOf(DEMO_ARTISTS[4]),
                artworkUrl = "https://picsum.photos/seed/album5/500/500",
                releaseYear = 2018,
                trackCount = 9,
                type = AlbumType.ALBUM
            ),
            Album(
                id = MediaId(PLUGIN_ID, "album-6"),
                name = "Dangerous Days",
                artists = listOf(DEMO_ARTISTS[5]),
                artworkUrl = "https://picsum.photos/seed/album6/500/500",
                releaseYear = 2014,
                trackCount = 13,
                type = AlbumType.ALBUM
            ),
            Album(
                id = MediaId(PLUGIN_ID, "album-7"),
                name = "OutRun",
                artists = listOf(DEMO_ARTISTS[6]),
                artworkUrl = "https://picsum.photos/seed/album7/500/500",
                releaseYear = 2013,
                trackCount = 10,
                type = AlbumType.ALBUM
            ),
            Album(
                id = MediaId(PLUGIN_ID, "album-8"),
                name = "Reflections",
                artists = listOf(DEMO_ARTISTS[3]),
                artworkUrl = "https://picsum.photos/seed/album8/500/500",
                releaseYear = 2020,
                trackCount = 12,
                type = AlbumType.ALBUM
            ),
            Album(
                id = MediaId(PLUGIN_ID, "album-9"),
                name = "Monsters",
                artists = listOf(DEMO_ARTISTS[0]),
                artworkUrl = "https://picsum.photos/seed/album9/500/500",
                releaseYear = 2020,
                trackCount = 15,
                type = AlbumType.ALBUM
            ),
            Album(
                id = MediaId(PLUGIN_ID, "album-10"),
                name = "New Model",
                artists = listOf(DEMO_ARTISTS[4]),
                artworkUrl = "https://picsum.photos/seed/album10/500/500",
                releaseYear = 2021,
                trackCount = 11,
                type = AlbumType.ALBUM
            )
        )
        
        private val DEMO_SONGS = listOf(
            // The Midnight - Nocturnal
            Song(id = MediaId(PLUGIN_ID, "song-1"), title = "Sunset", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[0], durationMs = 245000, trackNumber = 1, isPlayable = true, genres = listOf("Synthwave")),
            Song(id = MediaId(PLUGIN_ID, "song-2"), title = "Los Angeles", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[0], durationMs = 312000, trackNumber = 2, isPlayable = true, genres = listOf("Synthwave")),
            Song(id = MediaId(PLUGIN_ID, "song-3"), title = "Crystalline", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[0], durationMs = 278000, trackNumber = 3, isPlayable = true, genres = listOf("Synthwave")),
            Song(id = MediaId(PLUGIN_ID, "song-4"), title = "River of Darkness", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[0], durationMs = 301000, trackNumber = 4, isPlayable = true, genres = listOf("Synthwave")),
            // The Midnight - Kids
            Song(id = MediaId(PLUGIN_ID, "song-5"), title = "Kids", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[3], durationMs = 267000, trackNumber = 1, isPlayable = true, genres = listOf("Synthwave")),
            Song(id = MediaId(PLUGIN_ID, "song-6"), title = "Lost Boy", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[3], durationMs = 293000, trackNumber = 2, isPlayable = true, genres = listOf("Synthwave")),
            Song(id = MediaId(PLUGIN_ID, "song-7"), title = "America 2", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[3], durationMs = 256000, trackNumber = 3, isPlayable = true, genres = listOf("Synthwave")),
            // The Midnight - Monsters
            Song(id = MediaId(PLUGIN_ID, "song-8"), title = "Deep Blue", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[8], durationMs = 289000, trackNumber = 1, isPlayable = true, genres = listOf("Synthwave")),
            Song(id = MediaId(PLUGIN_ID, "song-9"), title = "Monsters", artists = listOf(DEMO_ARTISTS[0]), album = DEMO_ALBUMS[8], durationMs = 312000, trackNumber = 2, isPlayable = true, genres = listOf("Synthwave")),
            // FM-84 - Atlas
            Song(id = MediaId(PLUGIN_ID, "song-10"), title = "Running in the Night", artists = listOf(DEMO_ARTISTS[1]), album = DEMO_ALBUMS[1], durationMs = 289000, trackNumber = 1, isPlayable = true, genres = listOf("Synthwave", "Retrowave")),
            Song(id = MediaId(PLUGIN_ID, "song-11"), title = "Every Road", artists = listOf(DEMO_ARTISTS[1]), album = DEMO_ALBUMS[1], durationMs = 276000, trackNumber = 2, isPlayable = true, genres = listOf("Synthwave", "Retrowave")),
            Song(id = MediaId(PLUGIN_ID, "song-12"), title = "Wild Ones", artists = listOf(DEMO_ARTISTS[1]), album = DEMO_ALBUMS[1], durationMs = 301000, trackNumber = 3, isPlayable = true, genres = listOf("Synthwave", "Retrowave")),
            // Gunship - Dark All Day
            Song(id = MediaId(PLUGIN_ID, "song-13"), title = "Dark All Day", artists = listOf(DEMO_ARTISTS[2]), album = DEMO_ALBUMS[2], durationMs = 298000, trackNumber = 1, isPlayable = true, genres = listOf("Darksynth")),
            Song(id = MediaId(PLUGIN_ID, "song-14"), title = "When You Grow Up", artists = listOf(DEMO_ARTISTS[2]), album = DEMO_ALBUMS[2], durationMs = 324000, trackNumber = 2, isPlayable = true, genres = listOf("Darksynth")),
            Song(id = MediaId(PLUGIN_ID, "song-15"), title = "Art3mis & Parzival", artists = listOf(DEMO_ARTISTS[2]), album = DEMO_ALBUMS[2], durationMs = 267000, trackNumber = 3, isPlayable = true, genres = listOf("Darksynth")),
            // Timecop1983 - Reflections
            Song(id = MediaId(PLUGIN_ID, "song-16"), title = "On the Run", artists = listOf(DEMO_ARTISTS[3]), album = DEMO_ALBUMS[7], durationMs = 245000, trackNumber = 1, isPlayable = true, genres = listOf("Synthwave", "Retrowave")),
            Song(id = MediaId(PLUGIN_ID, "song-17"), title = "Reflections", artists = listOf(DEMO_ARTISTS[3]), album = DEMO_ALBUMS[7], durationMs = 278000, trackNumber = 2, isPlayable = true, genres = listOf("Synthwave", "Retrowave")),
            Song(id = MediaId(PLUGIN_ID, "song-18"), title = "Back to You", artists = listOf(DEMO_ARTISTS[3]), album = DEMO_ALBUMS[7], durationMs = 256000, trackNumber = 3, isPlayable = true, genres = listOf("Synthwave", "Retrowave")),
            // Carpenter Brut - Leather Teeth
            Song(id = MediaId(PLUGIN_ID, "song-19"), title = "Leather Teeth", artists = listOf(DEMO_ARTISTS[4]), album = DEMO_ALBUMS[4], durationMs = 312000, trackNumber = 1, isPlayable = true, genres = listOf("Darksynth")),
            Song(id = MediaId(PLUGIN_ID, "song-20"), title = "Cheerleader Effect", artists = listOf(DEMO_ARTISTS[4]), album = DEMO_ALBUMS[4], durationMs = 289000, trackNumber = 2, isPlayable = true, genres = listOf("Darksynth")),
            Song(id = MediaId(PLUGIN_ID, "song-21"), title = "Beware the Beast", artists = listOf(DEMO_ARTISTS[4]), album = DEMO_ALBUMS[4], durationMs = 301000, trackNumber = 3, isPlayable = true, genres = listOf("Darksynth")),
            // Carpenter Brut - New Model
            Song(id = MediaId(PLUGIN_ID, "song-22"), title = "Fab Tool", artists = listOf(DEMO_ARTISTS[4]), album = DEMO_ALBUMS[9], durationMs = 267000, trackNumber = 1, isPlayable = true, genres = listOf("Darksynth")),
            Song(id = MediaId(PLUGIN_ID, "song-23"), title = "The Widow Maker", artists = listOf(DEMO_ARTISTS[4]), album = DEMO_ALBUMS[9], durationMs = 293000, trackNumber = 2, isPlayable = true, genres = listOf("Darksynth")),
            // Perturbator - Dangerous Days
            Song(id = MediaId(PLUGIN_ID, "song-24"), title = "Future Club", artists = listOf(DEMO_ARTISTS[5]), album = DEMO_ALBUMS[5], durationMs = 312000, trackNumber = 1, isPlayable = true, genres = listOf("Darksynth", "Electronic")),
            Song(id = MediaId(PLUGIN_ID, "song-25"), title = "Dangerous Days", artists = listOf(DEMO_ARTISTS[5]), album = DEMO_ALBUMS[5], durationMs = 301000, trackNumber = 2, isPlayable = true, genres = listOf("Darksynth", "Electronic")),
            Song(id = MediaId(PLUGIN_ID, "song-26"), title = "Complete Domination", artists = listOf(DEMO_ARTISTS[5]), album = DEMO_ALBUMS[5], durationMs = 278000, trackNumber = 3, isPlayable = true, genres = listOf("Darksynth", "Electronic")),
            // Kavinsky - OutRun
            Song(id = MediaId(PLUGIN_ID, "song-27"), title = "Nightcall", artists = listOf(DEMO_ARTISTS[6]), album = DEMO_ALBUMS[6], durationMs = 245000, trackNumber = 1, isPlayable = true, genres = listOf("Synthwave", "Retrowave")),
            Song(id = MediaId(PLUGIN_ID, "song-28"), title = "Roadgame", artists = listOf(DEMO_ARTISTS[6]), album = DEMO_ALBUMS[6], durationMs = 289000, trackNumber = 2, isPlayable = true, genres = listOf("Synthwave", "Retrowave")),
            Song(id = MediaId(PLUGIN_ID, "song-29"), title = "Testarossa Autodrive", artists = listOf(DEMO_ARTISTS[6]), album = DEMO_ALBUMS[6], durationMs = 267000, trackNumber = 3, isPlayable = true, genres = listOf("Synthwave", "Retrowave")),
            Song(id = MediaId(PLUGIN_ID, "song-30"), title = "Odd Look", artists = listOf(DEMO_ARTISTS[6]), album = DEMO_ALBUMS[6], durationMs = 256000, trackNumber = 4, isPlayable = true, genres = listOf("Synthwave", "Retrowave"))
        )
        
        private val DEMO_PLAYLISTS = listOf(
            Playlist(
                id = MediaId(PLUGIN_ID, "playlist-1"),
                name = "Synthwave Essentials",
                description = "The best synthwave tracks from all artists",
                artworkUrl = "https://picsum.photos/seed/playlist1/500/500",
                ownerName = "ViPER Player",
                songCount = 12,
                isPublic = true
            ),
            Playlist(
                id = MediaId(PLUGIN_ID, "playlist-2"),
                name = "Night Drive",
                description = "Perfect for late night driving through the city",
                artworkUrl = "https://picsum.photos/seed/playlist2/500/500",
                ownerName = "ViPER Player",
                songCount = 10,
                isPublic = true
            ),
            Playlist(
                id = MediaId(PLUGIN_ID, "playlist-3"),
                name = "Darksynth Collection",
                description = "The darkest and heaviest synthwave tracks",
                artworkUrl = "https://picsum.photos/seed/playlist3/500/500",
                ownerName = "ViPER Player",
                songCount = 8,
                isPublic = true
            ),
            Playlist(
                id = MediaId(PLUGIN_ID, "playlist-4"),
                name = "Retrowave Classics",
                description = "Classic retrowave hits from the golden era",
                artworkUrl = "https://picsum.photos/seed/playlist4/500/500",
                ownerName = "ViPER Player",
                songCount = 9,
                isPublic = true
            ),
            Playlist(
                id = MediaId(PLUGIN_ID, "playlist-5"),
                name = "The Midnight Mix",
                description = "All the best tracks from The Midnight",
                artworkUrl = "https://picsum.photos/seed/playlist5/500/500",
                ownerName = "ViPER Player",
                songCount = 6,
                isPublic = true
            ),
            Playlist(
                id = MediaId(PLUGIN_ID, "playlist-6"),
                name = "Workout Energy",
                description = "High-energy synthwave for your workout",
                artworkUrl = "https://picsum.photos/seed/playlist6/500/500",
                ownerName = "ViPER Player",
                songCount = 7,
                isPublic = true
            ),
            Playlist(
                id = MediaId(PLUGIN_ID, "playlist-7"),
                name = "Chill Synthwave",
                description = "Relaxing synthwave for studying or relaxing",
                artworkUrl = "https://picsum.photos/seed/playlist7/500/500",
                ownerName = "ViPER Player",
                songCount = 8,
                isPublic = true
            ),
            Playlist(
                id = MediaId(PLUGIN_ID, "playlist-8"),
                name = "2020s New Releases",
                description = "Fresh synthwave releases from 2020 onwards",
                artworkUrl = "https://picsum.photos/seed/playlist8/500/500",
                ownerName = "ViPER Player",
                songCount = 5,
                isPublic = true
            )
        )
        
        // Map playlist IDs to their songs
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
                pluginId = PLUGIN_ID,
                name = "New Releases",
                description = "Latest releases from 2020 onwards",
                imageUrl = "https://picsum.photos/seed/cat1/400/200",
                contentType = CategoryContentType.ALBUMS
            ),
            BrowseCategory(
                id = "top-songs",
                pluginId = PLUGIN_ID,
                name = "Top Songs",
                description = "Most popular tracks",
                imageUrl = "https://picsum.photos/seed/cat2/400/200",
                contentType = CategoryContentType.SONGS
            ),
            BrowseCategory(
                id = "featured-playlists",
                pluginId = PLUGIN_ID,
                name = "Featured Playlists",
                description = "Curated playlists for every mood",
                imageUrl = "https://picsum.photos/seed/cat3/400/200",
                contentType = CategoryContentType.PLAYLISTS
            ),
            BrowseCategory(
                id = "genres-synthwave",
                pluginId = PLUGIN_ID,
                name = "Synthwave",
                description = "Classic synthwave tracks",
                imageUrl = "https://picsum.photos/seed/cat4/400/200",
                contentType = CategoryContentType.SONGS
            ),
            BrowseCategory(
                id = "genres-electronic",
                pluginId = PLUGIN_ID,
                name = "Electronic",
                description = "Electronic music collection",
                imageUrl = "https://picsum.photos/seed/cat5/400/200",
                contentType = CategoryContentType.ALBUMS
            ),
            BrowseCategory(
                id = "artists",
                pluginId = PLUGIN_ID,
                name = "Artists",
                description = "Browse by artist",
                imageUrl = "https://picsum.photos/seed/cat6/400/200",
                contentType = CategoryContentType.ARTISTS
            ),
            BrowseCategory(
                id = "recently-played",
                pluginId = PLUGIN_ID,
                name = "Recently Played",
                description = "Your recently played tracks",
                imageUrl = "https://picsum.photos/seed/cat7/400/200",
                contentType = CategoryContentType.SONGS
            )
        )
    }
}

