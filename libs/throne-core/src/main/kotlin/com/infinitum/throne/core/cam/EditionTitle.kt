package com.infinitum.throne.core.cam

import com.infinitum.throne.core.home.EditionPolicy

/** Display name shared with the launcher, guard, and vault. A bad flag fails closed to ARCH//ANGEL. */
object EditionTitle {
    fun title(raw: String): String = EditionPolicy.parse(raw).label
}
