package com.karlis.moviestmdb

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.SearchView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.karlis.moviestmdb.databinding.ActivityMainBinding
import org.json.JSONException
import org.json.JSONObject
import java.lang.IndexOutOfBoundsException
import java.math.RoundingMode

class MainActivity : AppCompatActivity(), RecyclerAdapterList.OnItemClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var moviesDetailsList: ArrayList<MoviesDetails>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        getMoviesListObjects()
        setRecyclerView()
        setTitle()

    }


    private fun getMoviesListObjects(){
        moviesDetailsList = intent.getSerializableExtra("movies") as ArrayList<MoviesDetails>
        moviesDetailsList.sortByDescending { it.vote_average.toDouble() }

    }

    private fun setRecyclerView() {
        val adapter = RecyclerAdapterList(moviesDetailsList, this)
        val recycler = binding.MainRecyclerView
        recycler.adapter = adapter
        recycler.isNestedScrollingEnabled = false
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.setHasFixedSize(true)

        recycler.addOnScrollListener(object: RecyclerView.OnScrollListener(){
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 110 || dy < -110){
                    binding.MainProgressBar.visibility = View.VISIBLE
                }else{
                    binding.MainProgressBar.visibility = View.GONE
                }
            }

            @SuppressLint("RestrictedApi")
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when(newState){
                    0 -> {
                        binding.MainProgressBar.visibility = View.GONE
                    }

                }
            }
        })
    }

    private fun setTitle() {
        val numberOfMovies = intent.getStringExtra("moviesCount")
        this.title = "$numberOfMovies Popular Movies on TMDB"
    }

    override fun onItemClick(position: Int) {
        val clickedItem: MoviesDetails = moviesDetailsList[position]
        val intent = Intent(this, DetailScreen::class.java)
        intent.putExtra("movie_id", clickedItem.id)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu?.findItem(R.id.mainMenuSearch)

        if (searchItem != null) {

            val searchView = searchItem.actionView as SearchView
            searchView.queryHint = "Search for Movies"
            searchView.focusSearch(View.FOCUS_UP)
            searchView.onActionViewExpanded()

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

                override fun onQueryTextSubmit(query: String?): Boolean {
                    Log.d("onQueryTextSubmit: ", query.toString())
                    if (query != null) {
                        loadMovies(query)
                        searchView.clearFocus()
                        searchItem.collapseActionView()
                        searchView.setQuery("",false)
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    Log.d("OnQueryTextChange: ", newText.toString())
                    return true
                }

            })

            searchView.setOnQueryTextFocusChangeListener { v, _ ->
                searchView.setQuery("",false)
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(v.windowToken, 0)

            }

        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.toString()){
            "Favorites" -> {
                val intent = Intent(this, FavoritesScreen::class.java)
                intent.putExtra("fromDB", getDataFromSql())
                getDataFromSql()
                startActivity(intent)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("Recycle")
    private fun getDataFromSql(): ArrayList<MoviesDetails> {

        val fromDB: ArrayList<MoviesDetails> = ArrayList()

        var id = String()
        var lang = String()
        var popularity = String()
        var posterLink = String()
        var releaseDate = String()
        var movTitle = String()
        var rating = String()

        val helper = MyDBHelper(applicationContext)
        val db = helper.readableDatabase
        val rs = db.rawQuery("SELECT * FROM MOVIES", null)

        Log.d("Items in SQL:", rs.count.toString())
        Log.d("Column Count:", rs.columnCount.toString())

        if (rs.moveToFirst() && rs.columnCount >= 1){
            var isItem = true
            while (isItem){
                try {
                    id = rs.getString(1)
                    lang = rs.getString(2)
                    popularity = rs.getString(3)
                    posterLink = "https://image.tmdb.org/t/p/w500${rs.getString(4)}"
                    releaseDate = rs.getString(5)
                    movTitle = rs.getString(6)
                    rating = rs.getString(7)
                } catch (e: IndexOutOfBoundsException){
                    println(e)
                }
                val favoriteMovie = MoviesDetails(
                    id = id,
                    original_language = lang,
                    popularity = popularity,
                    poster_path = posterLink,
                    release_date = releaseDate,
                    title = movTitle,
                    vote_average = rating
                )
                fromDB.add(favoriteMovie)
                if (rs.isLast){
                    isItem = false
                } else {
                    rs.moveToNext()
                }
            }
        }

        Log.d("Favorite DB (Size):", fromDB.size.toString())
        return fromDB
    }

    private fun loadMovies(query: String?) {
        var listOfMovies: ArrayList<MoviesDetails> = ArrayList()
        val apiKey = "73619d549f33ccdf0116452a1f3f9427"
        val urlString =
            "https://api.themoviedb.org/3/search/movie?api_key=$apiKey&language=en-US&query=$query&page=1&include_adult=false"

        val volleyStringRequest = StringRequest(
            Request.Method.GET,
            urlString,
            { response ->
                val resultJson = JSONObject(response)

                // Lambda 2
                val roundDouble: (String?) -> String = { number ->
                    number!!.toDouble().toBigDecimal().setScale(0, RoundingMode.UP).toDouble()
                        .toInt()
                        .toString()
                }
                val myMovie = Data(
                    results = resultJson.getJSONArray("results")
                )
                val moviesTotal = myMovie.results.length()

                if (moviesTotal == 0) {
                    Toast.makeText(this, "No Results for \"${query}\"", Toast.LENGTH_LONG).show()
                    return@StringRequest
                }

                for (index in 0 until moviesTotal) {

                    // Lambda 1
                    val getStringFromJson: (String) -> String? = { item ->
                        try {
                            myMovie.results.getJSONObject(index).getString(item)
                        } catch (e: JSONException) {
                            println("Error")
                            "????"
                        }.toString()
                    }

                    val movie = MoviesDetails(
                        title = getStringFromJson("title")!!,
                        poster_path = "https://image.tmdb.org/t/p/w500${getStringFromJson("poster_path")!!}",
                        vote_average = getStringFromJson("vote_average")!!.toDouble().toString(),
                        release_date = getStringFromJson("release_date")!!.take(4),
                        original_language = getStringFromJson("original_language")!!.uppercase(),
                        popularity = roundDouble(getStringFromJson("popularity")!!),
                        id = getStringFromJson("id")!!
                    )
                    Log.d("Movie Object:", movie.toString())
                    listOfMovies.add(movie)

                }

                val intent = Intent(this, SearchResults::class.java)
                intent.putExtra("Search", listOfMovies)
                intent.putExtra("Keyword", query)
                startActivity(intent)

            },
            { volleyError ->
                Log.d("Volley error msg: ", volleyError.toString())
            }
        )
        Volley.newRequestQueue(this).add(volleyStringRequest)
    }



}