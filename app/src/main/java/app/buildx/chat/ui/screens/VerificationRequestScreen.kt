package app.buildx.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalLayoutApi::class)
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
    var selectedCategory by remember { mutableStateOf(strings.categoryTech) }
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
        // Top Bar with verified badge icon in header
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_verified_badge),
                    contentDescription = null,
                    tint = colors.primaryBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.requestVerificationTitle,
                    color = colors.primaryText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(colors.primaryBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_verified_badge),
                                contentDescription = null,
                                tint = colors.primaryBlue,
                                modifier = Modifier.size(44.dp)
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
                            text = strings.accountVerifiedDesc,
                            color = colors.secondaryText,
                            fontSize = 14.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp
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
                            .padding(22.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD97706))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = strings.verificationUnderReview,
                                color = colors.primaryText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = strings.verificationReviewDesc,
                            color = colors.secondaryText,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )

                        if (!verificationStatus.fullName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
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

                        Spacer(modifier = Modifier.height(22.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .border(1.2.dp, colors.destructiveRed.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                                .clickable { showWithdrawConfirm = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.verificationWithdrawButton,
                                color = colors.destructiveRed,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                return@Column
            }

            // Case 3: Rejected Request
            if (!forceNewForm && verificationStatus?.hasRequest == true && verificationStatus.status == "rejected") {
                GroupedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(colors.destructiveRed)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = strings.verificationStatusRejected,
                                color = colors.destructiveRed,
                                fontSize = 17.sp,
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
                        Spacer(modifier = Modifier.height(18.dp))
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
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                return@Column
            }

            // Case 4: Application Form with Extended Conditions & Guidelines
            GroupedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_verified_badge),
                            contentDescription = null,
                            tint = colors.primaryBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = strings.verificationCriteriaHeading,
                            color = colors.primaryText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = strings.verificationCriteriaBody,
                        color = colors.secondaryText,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Structured Condition Checklist
                    ConditionRow(
                        title = if (strings.isRtl) "١. الأصالة والهوية الحقيقية" else "1. Authenticity & Real Identity",
                        description = if (strings.isRtl) "يجب أن يمثل الحساب شخصية حقيقية، أو علامة تجارية مسجلة، أو مشروعاً رسمياً." else "Account must represent a real individual, registered organization, or official project."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ConditionRow(
                        title = if (strings.isRtl) "٢. النشاط والأعمال الموثقة" else "2. Public Presence & Track Record",
                        description = if (strings.isRtl) "يجب أن يكون لديك نشاط أو أعمال سابقة موثقة (مثل مستودعات GitHub، موقع رسمي، أو مقالات منشورة)." else "Active presence, verified repositories, official portfolio, or recognized contributions."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ConditionRow(
                        title = if (strings.isRtl) "٣. اكتمال الملف الشخصي" else "3. Profile Completeness",
                        description = if (strings.isRtl) "يجب أن يحتوي ملفك على صورة شخصية، ونبذة تعريفية، واسم مستخدم محدد مسبقاً." else "Your profile must have an avatar, a bio, and a confirmed username."
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

            // Form inputs
            GroupedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Category selector using responsive FlowRow so pills NEVER cut off
                    Text(
                        text = strings.verificationCategoryLabel,
                        color = colors.primaryText,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { (label, key) ->
                            val isSelected = selectedCategory == key
                            Box(
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(if (isSelected) colors.primaryBlue else colors.surfaceMuted)
                                    .clickable { selectedCategory = key }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) colors.onPrimaryBlue else colors.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Full legal name
                    Text(
                        text = strings.verificationFullNameLabel,
                        color = colors.primaryText,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    VerificationTextField(
                        value = fullName,
                        onValueChange = { fullName = it; validationError = null },
                        hintText = "e.g. John Doe / Studio X"
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Evidence / Verification Links
                    Text(
                        text = strings.verificationLinksLabel,
                        color = colors.primaryText,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (strings.isRtl) "أضف روابط لموقعك، GitHub، أو حساباتك الموثقة" else "Links to your website, GitHub, or public profiles",
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    VerificationTextField(
                        value = links,
                        onValueChange = { links = it; validationError = null },
                        hintText = "https://github.com/..., https://..."
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Reason / statement (10 - 500 chars)
                    Text(
                        text = strings.verificationReasonLabel,
                        color = colors.primaryText,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (strings.isRtl) "اشرح بإيجاز لماذا يجب توثيق حسابك وما الذي تمثله (١٠ أحرف كحد أدنى)" else "Briefly explain why your account should be verified (min 10 characters)",
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    VerificationTextArea(
                        value = reason,
                        onValueChange = { reason = it; validationError = null },
                        hintText = strings.statementPlaceholder
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

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
                                validationError = if (strings.isRtl) "يرجى إدخال الاسم الكامل أو اسم الجهة." else "Please enter your full legal or brand name."
                                return@pressScaleClickable
                            }
                            if (reason.trim().length < 10) {
                                validationError = if (strings.isRtl) "يرجى تقديم سبب من ١٠ أحرف على الأقل." else "Please provide at least 10 characters explaining your request."
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

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun ConditionRow(
    title: String,
    description: String
) {
    val colors = BuildXTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceMuted)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = colors.primaryText,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = description,
            color = colors.secondaryText,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )
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
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceMuted)
            .padding(horizontal = 16.dp),
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
                fontSize = 14.5.sp,
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
            .height(115.dp)
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
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(colors.primaryBlue),
            modifier = Modifier.fillMaxSize()
        )
    }
}
