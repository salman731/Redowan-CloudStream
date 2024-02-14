package com.redowan

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse

import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.ArrayList


import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CircleFtpProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "http://15.1.1.50:5000" 
    override var name = "Circle FTP"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )
    override var lang = "bn"


    // enable this when your provider has a main page
    override val hasMainPage = false

    override val mainPage = mainPageOf(
        "80" to "Featured",
        "6" to "English Movies",
        "9" to "English & Foreign TV Series",
        "2" to "Hindi Movies",
        "5" to "Hindi TV Series",
    )


    private fun getJson(url: String): String {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful){
            return response.body.string()
        }
    }


    private fun toSearchResult(post: Post): SearchResponse? {
        if (post.type == "singleVideo" || post.type == "series"){
            return newMovieSearchResponse(post.title, post.id.toString(), TvType.Movie) {
                this.posterUrl = "$mainUrl/uploads/${post.imageSm}"
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val jsonString = getJson("$mainUrl/api/posts?searchTerm=$query&order=desc")
        val gson = Gson()
        val type = object : TypeToken<Map<String, List<Post>>>() {}.type
        val searchResponse = gson.fromJson<Map<String, List<Post>>>(jsonString, type)
        return searchResponse["posts"]?.mapNotNull { post ->
            toSearchResult(post)
        }?: listOf()
    }

    private fun extractLinksAndNames(input: String): List<Pair<String, String>> {
        val regex = "\\{(.*?), (.*?)\\}"
        val matches = Regex(regex).findAll(input)

        return matches.map { match ->
            Pair(match.groupValues[1].replace("link=", ""), match.groupValues[2].replace("title=", ""))
        }.toList()
    }

    override suspend fun load(url: String): LoadResponse {
        val jsonString = getJson("$mainUrl/api/posts/$url")
        val gson = Gson()
        val type = object : TypeToken<Data>() {}.type
        val data = gson.fromJson<Data>(jsonString, type)

        val title = data.title
        val poster ="$mainUrl/uploads/${data.imageSm}"
        val description = data.metaData
        val year = data.year?.toInt()
        when (data.content) {
            is List<*> -> {
                val episodesData = mutableListOf<Episode>()
                var seasonNum = 0
                data.content.forEach { season ->
                    seasonNum++
                    val episodeslist = season as Map<*, *>
                    val linksAndNames = extractLinksAndNames(episodeslist["episodes"].toString())
                    var episodenum = 0
                    for (pair in linksAndNames) {
                        episodenum ++
                        val episodeUrl = pair.first
                        val episodeName = pair.second
                        episodesData.add(Episode(
                                episodeUrl,
                                episodeName,
                                seasonNum,
                                episodenum
                            )
                        )
                    }
                }
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                }
            }


            is String -> {
                val dataurl = data.content
                return newMovieLoadResponse(title, url, TvType.Movie, dataurl) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                }
            }
        }
    }    

    data class Post(
        val id: String,
        val title: String,
        val imageSm: String?,
        val type: String?
    )

    data class Data(
        val title: String,
        val type: String?,
        val imageSm: String?,
        val metaData: String?,
        val content: Any,
        val name: String?,
        val quality: String?,
        val watchTime: String?,
        val year: String?
    )
}