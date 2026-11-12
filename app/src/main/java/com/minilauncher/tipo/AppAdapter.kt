package com.minilauncher.tipo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class AppAdapter(
    private val inflater: LayoutInflater,
    private val repo: AppRepository
) : BaseAdapter() {

    override fun getCount(): Int = repo.count

    override fun getItem(position: Int): Any = repo.entryAt(position)

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val tv = (convertView ?: inflater.inflate(R.layout.item_app, parent, false)) as TextView
        tv.text = repo.entryAt(position).label
        return tv
    }
}
