/*
 * (C) Copyright 2023 Lukas Morawietz (https://github.com/F43nd1r)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.faendir.acra.ui.view

import com.faendir.acra.common.UiTest
import com.faendir.acra.withAuth
import com.github.mvysny.kaributesting.v10._expectNone
import com.github.mvysny.kaributesting.v10._expectOne
import com.github.mvysny.kaributools.navigateTo
import org.junit.jupiter.api.Test

class AboutViewTest : UiTest() {
    @Test
    fun `should load`() {
        withAuth {
            navigateTo(AboutView::class)

            _expectOne<AboutView>()
        }
    }

    @Test
    fun `should require login`() {
        navigateTo(AboutView::class)

        _expectNone<AboutView>()
    }
}