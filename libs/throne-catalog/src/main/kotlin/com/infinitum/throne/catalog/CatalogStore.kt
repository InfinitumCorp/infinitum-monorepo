package com.infinitum.throne.catalog

import android.content.Context
import com.infinitum.throne.core.catalog.Product
import com.infinitum.throne.core.catalog.ProductCatalog

object CatalogStore {
    fun load(context: Context): List<Product> {
        return context.assets.open("products.json").bufferedReader().use { reader ->
            ProductCatalog.parse(reader.readText())
        }
    }
}
