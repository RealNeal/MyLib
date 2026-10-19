package com.rn.library.ui.components

import androidx.compose.runtime.Composable
import com.rn.library.data.WorkRepository
import com.rn.library.parser.MediaSource
import com.rn.library.parser.ParsedWorkMetadata
import com.rn.library.parser.ShikimoriFilterType

@Composable
fun ShikimoriSearchDialog(
    initialQuery: String = "",
    initialFilter: ShikimoriFilterType = ShikimoriFilterType.ALL,
    workId: String,
    repository: WorkRepository,
    onDismiss: () -> Unit,
    onWorkSelected: (ParsedWorkMetadata, localCoverPath: String?) -> Unit
) {
    NetworkSearchDialog(
        initialQuery = initialQuery,
        initialSource = MediaSource.SHIKIMORI,
        initialFilter = initialFilter,
        workId = workId,
        repository = repository,
        onDismiss = onDismiss,
        onWorkSelected = onWorkSelected
    )
}
