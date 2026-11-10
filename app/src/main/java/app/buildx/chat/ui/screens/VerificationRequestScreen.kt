package app.buildx.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.VerificationStatusInfo
import app.buildx.chat.ui.components.GroupedCard
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXTheme

@Composable
fun VerificationRequestScreen(
    verificationStatus: VerificationStatusInfo?,
    isLoading: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    onLoadStatus: () -> Unit,
    onSubmitRequest: (fullName: String, category: String, links: String, reason: String) -> Unit,
    onWithdrawRequest: (requestId: String?) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    LaunchedEffect(Unit) {
        onLoadStatus()
    }

    var fullName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tech & Development") }
    var links by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showWithdrawConfirm by remember { mutableStateOf(false) }
    var forceNewForm by remember { mutableStateOf(false) }

    val categories = listOf(
        strings.categoryTech to "tech",
        strings.categoryCreator to "creator",
        strings.categoryBusiness to "business",
        strings.categoryEducation to "education",
        strings.categoryMedia to "media"
    )

    if (showWithdrawConfirm) {
        AlertDialog(
            onDismissRequest = { showWithdrawConfirm = false },
            title = {
                Text(
                    text = strings.withdrawConfirmTitle,
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = strings.withdrawConfirmBody,
                    color = colors.secondaryText,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWithdrawConfirm = false
                        onWithdrawRequest(verificationStatus?.requestId)
                    }
                ) {
                    Text(
                        text = strings.withdrawConfirmAction,
                        color = colors.destructiveRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawConfirm = false }) {
                    Text(text = strings.cancelButton, color = colors.primaryText)
                }
            },
            containerColor = colors.cardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceMuted)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_back,
                contentDescription = strings.closeButton,
                onClick = onBackClick
            )
            Text(
                text = strings.requestVerificationTitle,
                color = colors.primaryText,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.size(44.dp))
        }

        if (isLoading && verificationStatus == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MarkLoader(size = 56.dp, isSpinning = true)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Case 1: Already Verified
            if (verificationStatus?.isVerified == true) {
                GroupedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(colors.primaryBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_verified_badge),
                                contentDescription = null,
                                tint = colors.primaryBlue,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = strings.verificationStatusApproved,
                            color = colors.primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.verificationCriteriaBody,
                            color = colors.secondaryText,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
                return@Column
            }

            // Case 2: Pending Request
            if (!forceNewForm && verificationStatus?.hasRequest == true && verificationStatus.status == "pending") {
                GroupedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Color(0xFFD97706))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.verificationStatusPending,
                                color = colors.primaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = strings.verificationCriteriaBody,
                            color = colors.secondaryText,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )

                        if (!verificationStatus.fullName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "${strings.verificationFullNameLabel}: ${verificationStatus.fullName}",
                                color = colors.primaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (!verificationStatus.category.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${strings.verificationCategoryLabel}: ${verificationStatus.category}",
                                color = colors.secondaryText,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .border(1.dp, colors.destructiveRed.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                                .clickable { showWithdrawConfirm = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.verificationWithdrawButton,
                                color = colors.destructiveRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                return@Column
            }

            // Case 3: Rejected Request (Show notice + option to retry)
            if (!forceNewForm && verificationStatus?.hasRequest == true && verificationStatus.status == "rejected") {
                GroupedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(colors.destructiveRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.verificationStatusRejected,
                                color = colors.destructiveRed,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!verificationStatus.decisionReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = verificationStatus.decisionReason,
                                color = colors.secondaryText,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.primaryBlue)
                                .clickable { forceNewForm = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.retryButton,
                                color = colors.onPrimaryBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                return@Column
            }

            // Case 4: Application Form
            // Criteria information card
            GroupedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.verificationCriteriaHeading,
                        color = colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.verificationCriteriaBody,
                        color = colors.secondaryText,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error alerts
            val displayErr = validationError ?: errorMessage
            if (!displayErr.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.destructiveRed.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = displayErr,
                        color = colors.destructiveRed,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Form card
            GroupedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Category selector pills
                    Text(
                        text = strings.verificationCategoryLabel,
                        color = colors.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.take(3).forEach { (label, key) ->
                            val isSelected = selectedCategory == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (isSelected) colors.primaryBlue else colors.surfaceMuted)
                                    .clickable { selectedCategory = key },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) colors.onPrimaryBlue else colors.secondaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.drop(3).forEach { (label, key) ->
                            val isSelected = selectedCategory == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (isSelected) colors.primaryBlue else colors.surfaceMuted)
                                    .clickable { selectedCategory = key },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) colors.onPrimaryBlue else colors.secondaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Full legal name
                    Text(
                        text = strings.verificationFullNameLabel,
                        color = colors.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    VerificationTextField(
                        value = fullName,
                        onValueChange = { fullName = it; validationError = null },
                        hintText = "e.g. John Doe / Studio X"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Links / proof
                    Text(
                        text = strings.verificationLinksLabel,
                        color = colors.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    VerificationTextField(
                        value = links,
                        onValueChange = { links = it; validationError = null },
                        hintText = "https://github.com/..., https://..."
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reason / statement (10 - 500 chars)
                    Text(
                        text = strings.verificationReasonLabel,
                        color = colors.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    VerificationTextArea(
                        value = reason,
                        onValueChange = { reason = it; validationError = null },
                        hintText = strings.statementPlaceholder
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (isSubmitting) colors.primaryBlue.copy(alpha = 0.5f) else colors.primaryBlue
                    )
                    .pressScaleClickable(
                        enabled = !isSubmitting,
                        onClick = {
                            if (fullName.trim().length < 2) {
                                validationError = "Please enter your full legal or brand name."
                                return@pressScaleClickable
                            }
                            if (reason.trim().length < 10) {
                                validationError = "Please provide at least 10 characters explaining your request."
                                return@pressScaleClickable
                            }
                            onSubmitRequest(
                                fullName.trim(),
                                selectedCategory,
                                links.trim(),
                                reason.trim()
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSubmitting) strings.doneLabel else strings.verificationSubmitButton,
                    color = colors.onPrimaryBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun VerificationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hintText: String
) {
    val colors = BuildXTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceMuted)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = hintText,
                color = colors.secondaryText,
                fontSize = 14.sp
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = colors.primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(colors.primaryBlue),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VerificationTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    hintText: String
) {
    val colors = BuildXTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceMuted)
            .padding(14.dp),
        contentAlignment = Alignment.TopStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = hintText,
                color = colors.secondaryText,
                fontSize = 14.sp
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= 500) onValueChange(it) },
            singleLine = false,
            textStyle = TextStyle(
                color = colors.primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(colors.primaryBlue),
            modifier = Modifier.fillMaxSize()
        )
    }
}
