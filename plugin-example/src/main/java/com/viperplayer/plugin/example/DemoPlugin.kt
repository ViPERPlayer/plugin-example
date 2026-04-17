package com.viperplayer.plugin.example

import com.viperplayer.plugin.AudioStreamWriter
import com.viperplayer.plugin.NotFoundException
import com.viperplayer.plugin.PagedResult
import com.viperplayer.plugin.ViperPlugin
import com.viperplayer.plugin.v1.Album
import com.viperplayer.plugin.v1.Artist
import com.viperplayer.plugin.v1.Artwork
import com.viperplayer.plugin.v1.AudioFormat
import com.viperplayer.plugin.v1.BrowseCategory
import com.viperplayer.plugin.v1.HomeContent
import com.viperplayer.plugin.v1.HomeSection
import com.viperplayer.plugin.v1.IHostCallbackV1
import com.viperplayer.plugin.v1.MediaItemV1
import com.viperplayer.plugin.v1.Playlist
import com.viperplayer.plugin.v1.PluginCapabilities
import com.viperplayer.plugin.v1.SearchFilter
import com.viperplayer.plugin.v1.SearchResult
import com.viperplayer.plugin.v1.SearchSuggestionsResultV1
import com.viperplayer.plugin.v1.Song
import com.viperplayer.plugin.v1.StreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Demo plugin that provides fake music data for testing.
 * This demonstrates how to implement a ViperPlugin.
 */
class DemoPlugin : ViperPlugin {
    
    private var host: IHostCallbackV1? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeStreams = mutableMapOf<String, Job>()
    
    override val capabilities = PluginCapabilities().apply {
        canSearch = true
        canBrowse = true
        hasLibrary = true
        hasPlaylists = true
        canSeek = true
        hasLyrics = false
        hasHighQuality = false
        supportsOffline = false
        hasSettings = false
        canEditPlaylists = false
        canSaveToLibrary = false
        hasRadio = false
        supportedQualities = intArrayOf(128, 256, 320)
    }
    
    override suspend fun onConnect(hostCallback: IHostCallbackV1) {
        this.host = hostCallback
    }
    
    override suspend fun onDisconnect() {
        host = null
        activeStreams.values.forEach { it.cancel() }
        activeStreams.clear()
    }
    
    // ==================== Search ====================

    override suspend fun getSearchSuggestions(query: String): SearchSuggestionsResultV1 {
        delay(100)
        val lowercaseQuery = query.lowercase()

        // Return matching items as suggestions
        val songs = DEMO_SONGS.filter {
            it.title.lowercase().contains(lowercaseQuery) ||
            it.artists.any { it.name.lowercase().contains(lowercaseQuery) }
        }.take(5).map { song ->
            MediaItemV1().apply {
                type = MediaItemV1.Type.SONG
                this.song = song
            }
        }

        val artists = DEMO_ARTISTS.filter {
            it.name.lowercase().contains(lowercaseQuery)
        }.take(5).map { artist ->
            MediaItemV1().apply {
                type = MediaItemV1.Type.ARTIST
                this.artist = artist
            }
        }

        val items = (songs + artists).take(10)

        return SearchSuggestionsResultV1().apply {
            suggestions = emptyList()
            this.items = items
        }
    }
    
    override suspend fun search(
        query: String,
        filter: SearchFilter?,
        cursor: String?,
        limit: Int
    ): SearchResult {
        // Simulate network delay
        delay(200)
        
        val lowercaseQuery = query.lowercase()
        
        val items = mutableListOf<MediaItemV1>()

        if (filter == null || filter == SearchFilter.SONG) {
            items.addAll(
                DEMO_SONGS.filter {
                    it.title.lowercase().contains(lowercaseQuery) ||
                    it.artists.any { it.name.lowercase().contains(lowercaseQuery) }
                }.take(limit).map { song ->
                    MediaItemV1().apply {
                        type = MediaItemV1.Type.SONG
                        this.song = song
                    }
                }
            )
        }

        if (filter == null || filter == SearchFilter.ALBUM) {
            items.addAll(
                DEMO_ALBUMS.filter {
                    it.name.lowercase().contains(lowercaseQuery) ||
                    it.artists.any { it.name.lowercase().contains(lowercaseQuery) }
                }.take(limit).map { album ->
                    MediaItemV1().apply {
                        type = MediaItemV1.Type.ALBUM
                        this.album = album
                    }
                }
            )
        }

        if (filter == null || filter == SearchFilter.ARTIST) {
            items.addAll(
                DEMO_ARTISTS.filter {
                    it.name.lowercase().contains(lowercaseQuery)
                }.take(limit).map { artist ->
                    MediaItemV1().apply {
                        type = MediaItemV1.Type.ARTIST
                        this.artist = artist
                    }
                }
            )
        }

        if (filter == null || filter == SearchFilter.PLAYLIST) {
            items.addAll(
                DEMO_PLAYLISTS.filter {
                    it.name.lowercase().contains(lowercaseQuery)
                }.take(limit).map { playlist ->
                    MediaItemV1().apply {
                        type = MediaItemV1.Type.PLAYLIST
                        this.playlist = playlist
                    }
                }
            )
        }

        return SearchResult().apply {
            this.items = items.take(limit)
            nextCursor = null // No pagination in demo
        }
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
        
        val items = when (categoryId) {
            "new-releases" -> DEMO_ALBUMS.filter { it.hasReleaseYear && it.releaseYear >= 2020 }.take(limit).map { album ->
                MediaItemV1().apply {
                    type = MediaItemV1.Type.ALBUM
                    this.album = album
                }
            }
            "top-songs" -> DEMO_SONGS.sortedByDescending { it.durationMs }.take(limit).map { song ->
                MediaItemV1().apply {
                    type = MediaItemV1.Type.SONG
                    this.song = song
                }
            }
            "featured-playlists" -> DEMO_PLAYLISTS.take(limit).map { playlist ->
                MediaItemV1().apply {
                    type = MediaItemV1.Type.PLAYLIST
                    this.playlist = playlist
                }
            }
            "genres-synthwave" -> DEMO_SONGS.filter { it.genres.contains("Synthwave") }.take(limit).map { song ->
                MediaItemV1().apply {
                    type = MediaItemV1.Type.SONG
                    this.song = song
                }
            }
            "genres-electronic" -> DEMO_ALBUMS.take(limit).map { album ->
                MediaItemV1().apply {
                    type = MediaItemV1.Type.ALBUM
                    this.album = album
                }
            }
            "artists" -> DEMO_ARTISTS.take(limit).map { artist ->
                MediaItemV1().apply {
                    type = MediaItemV1.Type.ARTIST
                    this.artist = artist
                }
            }
            "recently-played" -> DEMO_SONGS.shuffled().take(limit).map { song ->
                MediaItemV1().apply {
                    type = MediaItemV1.Type.SONG
                    this.song = song
                }
            }
            else -> emptyList()
        }

        return SearchResult().apply {
            this.items = items
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
    
    override suspend fun getSong(id: String): Song {
        delay(50)
        return DEMO_SONGS.find { it.id == id }
            ?: throw NotFoundException("Song not found: $id")
    }
    
    override suspend fun getAlbum(id: String): Album {
        delay(50)
        val album = DEMO_ALBUMS.find { it.id == id }
            ?: throw NotFoundException("Album not found: $id")
        
        // Return album with tracks populated
        val tracks = DEMO_SONGS.filter { it.album?.id == album.id }
        return album.apply {
            songs = tracks
        }
    }
    
    override suspend fun getArtist(id: String): Artist {
        delay(50)
        return DEMO_ARTISTS.find { it.id == id }
            ?: throw NotFoundException("Artist not found: $id")
    }
    
    override suspend fun getPlaylist(mediaId: String): Playlist {
        delay(50)
        val playlist = DEMO_PLAYLISTS.find { it.id == mediaId }
            ?: throw NotFoundException("Playlist not found: $mediaId")
        
        // Get songs for this specific playlist
        val playlistSongs = PLAYLIST_SONGS[playlist.id].orEmpty()
        return playlist.apply {
            songs = playlistSongs
        }
    }

    // ==================== Home Screen ====================

    override suspend fun getHomeSections(): HomeContent {
        delay(100)
        
        val quickPicks = DEMO_SONGS.shuffled().take(5).map { song ->
            MediaItemV1().apply {
                type = MediaItemV1.Type.SONG
                this.song = song
            }
        }
        
        val sections = listOf(
            // Custom "Made For You" section
            HomeSection().apply {
                id = "made_for_you"
                title = "Made For You"
                items = DEMO_PLAYLISTS.take(3).map { playlist ->
                    MediaItemV1().apply {
                        type = MediaItemV1.Type.PLAYLIST
                        this.playlist = playlist
                    }
                }
            },
            
            // Custom "New Releases" section
            HomeSection().apply {
                id = "new_releases"
                title = "New Releases"
                items = DEMO_ALBUMS.filter { it.hasReleaseYear && it.releaseYear >= 2020 }.take(5).map { album ->
                    MediaItemV1().apply {
                        type = MediaItemV1.Type.ALBUM
                        this.album = album
                    }
                }
            }
        )
        
        return HomeContent().apply {
            this.quickPicks = quickPicks
            this.sections = sections
        }
    }

    // ==================== Audio Streaming ====================
    
    override suspend fun getStream(mediaId: String): StreamSource {
        val song = getSong(mediaId)
        
        val writer = AudioStreamWriter.create(
            mediaId = mediaId,
            format = AudioFormat().apply {
                sampleRate = 44100
                channelCount = 2
                encoding = AudioFormat.PcmEncoding.PCM_16BIT
                bitDepth = 16
            },
            durationMs = if (song.hasDurationMs) song.durationMs else 0L,
            canSeek = true
        )
        
        // Start streaming in background
        val job = scope.launch {
            try {
                streamSilence(writer, if (song.hasDurationMs) song.durationMs else 0L)
            } catch (_: Exception) {
                // Stream ended or was cancelled
            } finally {
                writer.close()
            }
        }
        
        activeStreams[writer.streamId] = job
        
        return StreamSource().apply {
            audioStream = writer.audioStream
        }
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
        val bytesPerSecond = (format.sampleRate * format.channelCount * (format.bitDepth / 8))
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
            Artist().apply {
                id = "artist-1"
                name = "The Midnight"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/artist1/300/300"
                    full = "https://picsum.photos/seed/artist1/300/300"
                }
            },
            Artist().apply {
                id = "artist-2"
                name = "FM-84"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/artist2/300/300"
                    full = "https://picsum.photos/seed/artist2/300/300"
                }
            },
            Artist().apply {
                id = "artist-3"
                name = "Gunship"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/artist3/300/300"
                    full = "https://picsum.photos/seed/artist3/300/300"
                }
            },
            Artist().apply {
                id = "artist-4"
                name = "Timecop1983"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/artist4/300/300"
                    full = "https://picsum.photos/seed/artist4/300/300"
                }
            },
            Artist().apply {
                id = "artist-5"
                name = "Carpenter Brut"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/artist5/300/300"
                    full = "https://picsum.photos/seed/artist5/300/300"
                }
            },
            Artist().apply {
                id = "artist-6"
                name = "Perturbator"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/artist6/300/300"
                    full = "https://picsum.photos/seed/artist6/300/300"
                }
            },
            Artist().apply {
                id = "artist-7"
                name = "Kavinsky"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/artist7/300/300"
                    full = "https://picsum.photos/seed/artist7/300/300"
                }
            }
        )
        
        private val DEMO_ALBUMS = listOf(
            Album().apply {
                id = "album-1"
                name = "Nocturnal"
                artists = listOf(DEMO_ARTISTS[0])
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/album1/500/500"
                    full = "https://picsum.photos/seed/album1/500/500"
                }
                releaseYear = 2017
                hasReleaseYear = true
                trackCount = 12
                type = Album.AlbumType.ALBUM
            },
            Album().apply {
                id = "album-2"
                name = "Atlas"
                artists = listOf(DEMO_ARTISTS[1])
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/album2/500/500"
                    full = "https://picsum.photos/seed/album2/500/500"
                }
                releaseYear = 2016
                hasReleaseYear = true
                trackCount = 10
                type = Album.AlbumType.ALBUM
            },
            Album().apply {
                id = "album-3"
                name = "Dark All Day"
                artists = listOf(DEMO_ARTISTS[2])
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/album3/500/500"
                    full = "https://picsum.photos/seed/album3/500/500"
                }
                releaseYear = 2018
                hasReleaseYear = true
                trackCount = 14
                type = Album.AlbumType.ALBUM
            },
            Album().apply {
                id = "album-4"
                name = "Kids"
                artists = listOf(DEMO_ARTISTS[0])
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/album4/500/500"
                    full = "https://picsum.photos/seed/album4/500/500"
                }
                releaseYear = 2018
                hasReleaseYear = true
                trackCount = 11
                type = Album.AlbumType.ALBUM
            },
            Album().apply {
                id = "album-5"
                name = "Leather Teeth"
                artists = listOf(DEMO_ARTISTS[4])
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/album5/500/500"
                    full = "https://picsum.photos/seed/album5/500/500"
                }
                releaseYear = 2018
                hasReleaseYear = true
                trackCount = 9
                type = Album.AlbumType.ALBUM
            },
            Album().apply {
                id = "album-6"
                name = "Dangerous Days"
                artists = listOf(DEMO_ARTISTS[5])
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/album6/500/500"
                    full = "https://picsum.photos/seed/album6/500/500"
                }
                releaseYear = 2014
                hasReleaseYear = true
                trackCount = 13
                type = Album.AlbumType.ALBUM
            },
            Album().apply {
                id = "album-7"
                name = "OutRun"
                artists = listOf(DEMO_ARTISTS[6])
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/album7/500/500"
                    full = "https://picsum.photos/seed/album7/500/500"
                }
                releaseYear = 2013
                hasReleaseYear = true
                trackCount = 10
                type = Album.AlbumType.ALBUM
            },
            Album().apply {
                id = "album-8"
                name = "Reflections"
                artists = listOf(DEMO_ARTISTS[3])
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/album8/500/500"
                    full = "https://picsum.photos/seed/album8/500/500"
                }
                releaseYear = 2020
                hasReleaseYear = true
                trackCount = 12
                type = Album.AlbumType.ALBUM
            },
            Album().apply {
                id = "album-9"
                name = "Monsters"
                artists = listOf(DEMO_ARTISTS[0])
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/album9/500/500"
                    full = "https://picsum.photos/seed/album9/500/500"
                }
                releaseYear = 2020
                hasReleaseYear = true
                trackCount = 15
                type = Album.AlbumType.ALBUM
            },
            Album().apply {
                id = "album-10"
                name = "New Model"
                artists = listOf(DEMO_ARTISTS[4])
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/album10/500/500"
                    full = "https://picsum.photos/seed/album10/500/500"
                }
                releaseYear = 2021
                hasReleaseYear = true
                trackCount = 11
                type = Album.AlbumType.ALBUM
            }
        )
        
        private val DEMO_SONGS = listOf(
            // The Midnight - Nocturnal
            Song().apply { id = "song-1"; title = "Sunset"; artists = listOf(DEMO_ARTISTS[0]); album = DEMO_ALBUMS[0]; durationMs = 245000; hasDurationMs = true; trackNumber = 1; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave") },
            Song().apply { id = "song-2"; title = "Los Angeles"; artists = listOf(DEMO_ARTISTS[0]); album = DEMO_ALBUMS[0]; durationMs = 312000; hasDurationMs = true; trackNumber = 2; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave") },
            Song().apply { id = "song-3"; title = "Crystalline"; artists = listOf(DEMO_ARTISTS[0]); album = DEMO_ALBUMS[0]; durationMs = 278000; hasDurationMs = true; trackNumber = 3; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave") },
            Song().apply { id = "song-4"; title = "River of Darkness"; artists = listOf(DEMO_ARTISTS[0]); album = DEMO_ALBUMS[0]; durationMs = 301000; hasDurationMs = true; trackNumber = 4; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave") },
            // The Midnight - Kids
            Song().apply { id = "song-5"; title = "Kids"; artists = listOf(DEMO_ARTISTS[0]); album = DEMO_ALBUMS[3]; durationMs = 267000; hasDurationMs = true; trackNumber = 1; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave") },
            Song().apply { id = "song-6"; title = "Lost Boy"; artists = listOf(DEMO_ARTISTS[0]); album = DEMO_ALBUMS[3]; durationMs = 293000; hasDurationMs = true; trackNumber = 2; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave") },
            Song().apply { id = "song-7"; title = "America 2"; artists = listOf(DEMO_ARTISTS[0]); album = DEMO_ALBUMS[3]; durationMs = 256000; hasDurationMs = true; trackNumber = 3; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave") },
            // The Midnight - Monsters
            Song().apply { id = "song-8"; title = "Deep Blue"; artists = listOf(DEMO_ARTISTS[0]); album = DEMO_ALBUMS[8]; durationMs = 289000; hasDurationMs = true; trackNumber = 1; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave") },
            Song().apply { id = "song-9"; title = "Monsters"; artists = listOf(DEMO_ARTISTS[0]); album = DEMO_ALBUMS[8]; durationMs = 312000; hasDurationMs = true; trackNumber = 2; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave") },
            // FM-84 - Atlas
            Song().apply { id = "song-10"; title = "Running in the Night"; artists = listOf(DEMO_ARTISTS[1]); album = DEMO_ALBUMS[1]; durationMs = 289000; hasDurationMs = true; trackNumber = 1; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave", "Retrowave") },
            Song().apply { id = "song-11"; title = "Every Road"; artists = listOf(DEMO_ARTISTS[1]); album = DEMO_ALBUMS[1]; durationMs = 276000; hasDurationMs = true; trackNumber = 2; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave", "Retrowave") },
            Song().apply { id = "song-12"; title = "Wild Ones"; artists = listOf(DEMO_ARTISTS[1]); album = DEMO_ALBUMS[1]; durationMs = 301000; hasDurationMs = true; trackNumber = 3; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave", "Retrowave") },
            // Gunship - Dark All Day
            Song().apply { id = "song-13"; title = "Dark All Day"; artists = listOf(DEMO_ARTISTS[2]); album = DEMO_ALBUMS[2]; durationMs = 298000; hasDurationMs = true; trackNumber = 1; hasTrackNumber = true; isPlayable = true; genres = listOf("Darksynth") },
            Song().apply { id = "song-14"; title = "When You Grow Up"; artists = listOf(DEMO_ARTISTS[2]); album = DEMO_ALBUMS[2]; durationMs = 324000; hasDurationMs = true; trackNumber = 2; hasTrackNumber = true; isPlayable = true; genres = listOf("Darksynth") },
            Song().apply { id = "song-15"; title = "Art3mis & Parzival"; artists = listOf(DEMO_ARTISTS[2]); album = DEMO_ALBUMS[2]; durationMs = 267000; hasDurationMs = true; trackNumber = 3; hasTrackNumber = true; isPlayable = true; genres = listOf("Darksynth") },
            // Timecop1983 - Reflections
            Song().apply { id = "song-16"; title = "On the Run"; artists = listOf(DEMO_ARTISTS[3]); album = DEMO_ALBUMS[7]; durationMs = 245000; hasDurationMs = true; trackNumber = 1; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave", "Retrowave") },
            Song().apply { id = "song-17"; title = "Reflections"; artists = listOf(DEMO_ARTISTS[3]); album = DEMO_ALBUMS[7]; durationMs = 278000; hasDurationMs = true; trackNumber = 2; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave", "Retrowave") },
            Song().apply { id = "song-18"; title = "Back to You"; artists = listOf(DEMO_ARTISTS[3]); album = DEMO_ALBUMS[7]; durationMs = 256000; hasDurationMs = true; trackNumber = 3; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave", "Retrowave") },
            // Carpenter Brut - Leather Teeth
            Song().apply { id = "song-19"; title = "Leather Teeth"; artists = listOf(DEMO_ARTISTS[4]); album = DEMO_ALBUMS[4]; durationMs = 312000; hasDurationMs = true; trackNumber = 1; hasTrackNumber = true; isPlayable = true; genres = listOf("Darksynth") },
            Song().apply { id = "song-20"; title = "Cheerleader Effect"; artists = listOf(DEMO_ARTISTS[4]); album = DEMO_ALBUMS[4]; durationMs = 289000; hasDurationMs = true; trackNumber = 2; hasTrackNumber = true; isPlayable = true; genres = listOf("Darksynth") },
            Song().apply { id = "song-21"; title = "Beware the Beast"; artists = listOf(DEMO_ARTISTS[4]); album = DEMO_ALBUMS[4]; durationMs = 301000; hasDurationMs = true; trackNumber = 3; hasTrackNumber = true; isPlayable = true; genres = listOf("Darksynth") },
            // Carpenter Brut - New Model
            Song().apply { id = "song-22"; title = "Fab Tool"; artists = listOf(DEMO_ARTISTS[4]); album = DEMO_ALBUMS[9]; durationMs = 267000; hasDurationMs = true; trackNumber = 1; hasTrackNumber = true; isPlayable = true; genres = listOf("Darksynth") },
            Song().apply { id = "song-23"; title = "The Widow Maker"; artists = listOf(DEMO_ARTISTS[4]); album = DEMO_ALBUMS[9]; durationMs = 293000; hasDurationMs = true; trackNumber = 2; hasTrackNumber = true; isPlayable = true; genres = listOf("Darksynth") },
            // Perturbator - Dangerous Days
            Song().apply { id = "song-24"; title = "Future Club"; artists = listOf(DEMO_ARTISTS[5]); album = DEMO_ALBUMS[5]; durationMs = 312000; hasDurationMs = true; trackNumber = 1; hasTrackNumber = true; isPlayable = true; genres = listOf("Darksynth", "Electronic") },
            Song().apply { id = "song-25"; title = "Dangerous Days"; artists = listOf(DEMO_ARTISTS[5]); album = DEMO_ALBUMS[5]; durationMs = 301000; hasDurationMs = true; trackNumber = 2; hasTrackNumber = true; isPlayable = true; genres = listOf("Darksynth", "Electronic") },
            Song().apply { id = "song-26"; title = "Complete Domination"; artists = listOf(DEMO_ARTISTS[5]); album = DEMO_ALBUMS[5]; durationMs = 278000; hasDurationMs = true; trackNumber = 3; hasTrackNumber = true; isPlayable = true; genres = listOf("Darksynth", "Electronic") },
            // Kavinsky - OutRun
            Song().apply { id = "song-27"; title = "Nightcall"; artists = listOf(DEMO_ARTISTS[6]); album = DEMO_ALBUMS[6]; durationMs = 245000; hasDurationMs = true; trackNumber = 1; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave", "Retrowave") },
            Song().apply { id = "song-28"; title = "Roadgame"; artists = listOf(DEMO_ARTISTS[6]); album = DEMO_ALBUMS[6]; durationMs = 289000; hasDurationMs = true; trackNumber = 2; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave", "Retrowave") },
            Song().apply { id = "song-29"; title = "Testarossa Autodrive"; artists = listOf(DEMO_ARTISTS[6]); album = DEMO_ALBUMS[6]; durationMs = 267000; hasDurationMs = true; trackNumber = 3; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave", "Retrowave") },
            Song().apply { id = "song-30"; title = "Odd Look"; artists = listOf(DEMO_ARTISTS[6]); album = DEMO_ALBUMS[6]; durationMs = 256000; hasDurationMs = true; trackNumber = 4; hasTrackNumber = true; isPlayable = true; genres = listOf("Synthwave", "Retrowave") }
        )
        
        private val DEMO_PLAYLISTS = listOf(
            Playlist().apply {
                id = "playlist-1"
                name = "Synthwave Essentials"
                description = "The best synthwave tracks from all artists"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/playlist1/500/500"
                    full = "https://picsum.photos/seed/playlist1/500/500"
                }
                ownerName = "ViPER Player"
                songCount = 12
            },
            Playlist().apply {
                id = "playlist-2"
                name = "Night Drive"
                description = "Perfect for late night driving through the city"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/playlist2/500/500"
                    full = "https://picsum.photos/seed/playlist2/500/500"
                }
                ownerName = "ViPER Player"
                songCount = 10
            },
            Playlist().apply {
                id = "playlist-3"
                name = "Darksynth Collection"
                description = "The darkest and heaviest synthwave tracks"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/playlist3/500/500"
                    full = "https://picsum.photos/seed/playlist3/500/500"
                }
                ownerName = "ViPER Player"
                songCount = 8
            },
            Playlist().apply {
                id = "playlist-4"
                name = "Retrowave Classics"
                description = "Classic retrowave hits from the golden era"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/playlist4/500/500"
                    full = "https://picsum.photos/seed/playlist4/500/500"
                }
                ownerName = "ViPER Player"
                songCount = 9
            },
            Playlist().apply {
                id = "playlist-5"
                name = "The Midnight Mix"
                description = "All the best tracks from The Midnight"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/playlist5/500/500"
                    full = "https://picsum.photos/seed/playlist5/500/500"
                }
                ownerName = "ViPER Player"
                songCount = 6
            },
            Playlist().apply {
                id = "playlist-6"
                name = "Workout Energy"
                description = "High-energy synthwave for your workout"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/playlist6/500/500"
                    full = "https://picsum.photos/seed/playlist6/500/500"
                }
                ownerName = "ViPER Player"
                songCount = 7
            },
            Playlist().apply {
                id = "playlist-7"
                name = "Chill Synthwave"
                description = "Relaxing synthwave for studying or relaxing"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/playlist7/500/500"
                    full = "https://picsum.photos/seed/playlist7/500/500"
                }
                ownerName = "ViPER Player"
                songCount = 8
            },
            Playlist().apply {
                id = "playlist-8"
                name = "2020s New Releases"
                description = "Fresh synthwave releases from 2020 onwards"
                artwork = Artwork().apply {
                    thumbnail = "https://picsum.photos/seed/playlist8/500/500"
                    full = "https://picsum.photos/seed/playlist8/500/500"
                }
                ownerName = "ViPER Player"
                songCount = 5
            }
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
            BrowseCategory().apply {
                id = "new-releases"
                pluginId = PLUGIN_ID
                name = "New Releases"
                description = "Latest releases from 2020 onwards"
                imageUrl = "https://picsum.photos/seed/cat1/400/200"
                contentType = BrowseCategory.CategoryContentType.ALBUMS
            },
            BrowseCategory().apply {
                id = "top-songs"
                pluginId = PLUGIN_ID
                name = "Top Songs"
                description = "Most popular tracks"
                imageUrl = "https://picsum.photos/seed/cat2/400/200"
                contentType = BrowseCategory.CategoryContentType.SONGS
            },
            BrowseCategory().apply {
                id = "featured-playlists"
                pluginId = PLUGIN_ID
                name = "Featured Playlists"
                description = "Curated playlists for every mood"
                imageUrl = "https://picsum.photos/seed/cat3/400/200"
                contentType = BrowseCategory.CategoryContentType.PLAYLISTS
            },
            BrowseCategory().apply {
                id = "genres-synthwave"
                pluginId = PLUGIN_ID
                name = "Synthwave"
                description = "Classic synthwave tracks"
                imageUrl = "https://picsum.photos/seed/cat4/400/200"
                contentType = BrowseCategory.CategoryContentType.SONGS
            },
            BrowseCategory().apply {
                id = "genres-electronic"
                pluginId = PLUGIN_ID
                name = "Electronic"
                description = "Electronic music collection"
                imageUrl = "https://picsum.photos/seed/cat5/400/200"
                contentType = BrowseCategory.CategoryContentType.ALBUMS
            },
            BrowseCategory().apply {
                id = "artists"
                pluginId = PLUGIN_ID
                name = "Artists"
                description = "Browse by artist"
                imageUrl = "https://picsum.photos/seed/cat6/400/200"
                contentType = BrowseCategory.CategoryContentType.ARTISTS
            },
            BrowseCategory().apply {
                id = "recently-played"
                pluginId = PLUGIN_ID
                name = "Recently Played"
                description = "Your recently played tracks"
                imageUrl = "https://picsum.photos/seed/cat7/400/200"
                contentType = BrowseCategory.CategoryContentType.SONGS
            }
        )
    }
}
