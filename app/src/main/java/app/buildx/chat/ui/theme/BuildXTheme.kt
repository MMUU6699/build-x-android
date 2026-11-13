package app.buildx.chat.ui.theme

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.AppLanguage
import java.util.Locale

/**
 * Color tokens traced to video frames in docs/design/SPEC.md and docs/design/tokens.json.
 * Light theme values are [OBSERVED]; Dark theme values are [INFERRED] as a coherent dark surface system.
 */
@Immutable
data class BuildXColors(
    val isDark: Boolean,
    val pageBackground: Color,
    val surfaceMuted: Color,
    val cardBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val primaryBlue: Color,
    val onPrimaryBlue: Color,
    val destructiveRed: Color,
    val hairlineBorder: Color,
    val subtleSurfaceBorder: Color,
    val grabHandle: Color,
    val scrimColor: Color,
    val markPurpleStart: Color,
    val markVioletMid: Color,
    val markBlueEnd: Color,
    val composerShadowColor: Color,
    val headerHaloShadowColor: Color,
    val innerControlShadowColor: Color,
    val stopHaloBackground: Color
)

val LightBuildXColors = BuildXColors(
    isDark = false,
    pageBackground = Color(0xFFFFFFFF),        // [OBSERVED] f_0001
    surfaceMuted = Color(0xFFF1F3F0),          // [OBSERVED] f_0001 user bubble, sheets, settings bg
    cardBackground = Color(0xFFFFFFFF),        // [OBSERVED] f_0001 composer, settings cards
    primaryText = Color(0xFF0A0A0A),           // [OBSERVED] f_0001 near-black text
    secondaryText = Color(0xFF6E7274),         // [OBSERVED] f_0001 secondary/label gray
    primaryBlue = Color(0xFF0063D3),           // [OBSERVED] f_0090 stop button, links, primary CTA
    onPrimaryBlue = Color(0xFFFFFFFF),         // [OBSERVED] f_0065 white arrow/text on blue
    destructiveRed = Color(0xFFC3192E),        // [OBSERVED] f_1980 Log out crimson red
    hairlineBorder = Color(0xFFE1E3E1),        // [OBSERVED] f_0001 1.2dp hairline border on inner controls
    subtleSurfaceBorder = Color(0xFFECEEEC),   // [OBSERVED] soft contour on floating white buttons/cards
    grabHandle = Color(0xFFDADCD9),            // [OBSERVED] f_0510 sheet grab handle
    scrimColor = Color(0x4D000000),            // [OBSERVED] f_0510 30% black scrim (#B2B2B2 over white)
    markPurpleStart = Color(0xFFC24DFB),       // [OBSERVED] brand mark gradient start
    markVioletMid = Color(0xFF8842FA),         // [OBSERVED] brand mark gradient mid
    markBlueEnd = Color(0xFF4B3EF6),           // [OBSERVED] brand mark gradient end
    composerShadowColor = Color(0x0D000000),   // subtle Gaussian shadow (~5% black) under composer
    headerHaloShadowColor = Color(0x0D000000), // subtle halo (~5% black) on floating buttons
    innerControlShadowColor = Color(0x09000000), // subtle inner depth (~3.5% black)
    stopHaloBackground = Color(0xFFE8F0FE)     // [OBSERVED] f_0090 36dp light halo around stop square
)

val DarkBuildXColors = BuildXColors(
    isDark = true,
    pageBackground = Color(0xFF0E1012),        // [INFERRED] dark page background
    surfaceMuted = Color(0xFF1A1D20),          // [INFERRED] dark user bubble / sheet / settings bg
    cardBackground = Color(0xFF171A1E),        // [INFERRED] dark elevated card surface
    primaryText = Color(0xFFF3F5F6),           // [INFERRED] dark near-white primary text
    secondaryText = Color(0xFF9BA0A5),         // [INFERRED] dark secondary label gray
    primaryBlue = Color(0xFF3B8BF7),           // [INFERRED] accessible primary blue on dark
    onPrimaryBlue = Color(0xFFFFFFFF),         // [INFERRED]
    destructiveRed = Color(0xFFFF5A6E),        // [INFERRED] accessible crimson red on dark
    hairlineBorder = Color(0xFF2E3338),        // [INFERRED] dark 1.2dp hairline border
    subtleSurfaceBorder = Color(0xFF25292E),   // [INFERRED] dark floating surface contour
    grabHandle = Color(0xFF3E444B),            // [INFERRED] dark grab handle
    scrimColor = Color(0x73000000),            // [INFERRED] 45% black scrim in dark mode
    markPurpleStart = Color(0xFFC24DFB),       // [OBSERVED]
    markVioletMid = Color(0xFF8842FA),         // [OBSERVED]
    markBlueEnd = Color(0xFF4B3EF6),           // [OBSERVED]
    composerShadowColor = Color(0x33000000),   // clean Gaussian dark-mode composer shadow (~20% black)
    headerHaloShadowColor = Color(0x38000000), // clean floating header shadow (~22% black)
    innerControlShadowColor = Color(0x24000000), // subtle inner shadow (~14% black)
    stopHaloBackground = Color(0xFF1E324D)     // [INFERRED]
)

@Immutable
data class BuildXStrings(
    val isRtl: Boolean,
    val appName: String,
    val askBuildX: String,
    val askPrivately: String,
    val replyPlaceholder: String,
    val thinkingMode: String,
    val instantMode: String,
    val instantDescription: String,
    val thinkingDescription: String,
    val showThinking: String,
    val stepsTitle: String,
    val doneLabel: String,
    val responseStopped: String,
    val incognitoSubtitle: String,
    val incognitoEmptyNotice: String,
    val searchPlaceholder: String,
    val noMatchingConversations: String,
    val newChat: String,
    val mediaTitle: String,
    val chatsSection: String,
    val noConversationsYet: String,
    val notificationsTitle: String,
    val turnOnNotificationsTitle: String,
    val turnOnNotificationsBody: String,
    val turnOnButton: String,
    val noNotificationsTitle: String,
    val noNotificationsSubtitle: String,
    val noMediaTitle: String,
    val noMediaSubtitle: String,
    val libraryTitle: String,
    val seeAllButton: String,
    val attachSelectedPrefix: String,
    val recentlyUploaded: String,
    val filesLabel: String,
    val shareLink: String,
    val pinChat: String,
    val unpinChat: String,
    val renameChat: String,
    val deleteChat: String,
    val settingsTitle: String,
    val viewProfile: String,
    val appSettingsSection: String,
    val appAppearance: String,
    val appearanceAutomatic: String,
    val appearanceLight: String,
    val appearanceDark: String,
    val appLanguage: String,
    val languageSystem: String,
    val languageEnglish: String,
    val languageArabic: String,
    val helpAndSupport: String,
    val dataAndPrivacy: String,
    val appInfo: String,
    val appVersionLabel: String,
    val buildNumberLabel: String,
    val legalAndSafety: String,
    val logOut: String,
    val profileTitle: String,
    val changePhoto: String,
    val displayNameLabel: String,
    val handleLabel: String,
    val saveChanges: String,
    val exportMyData: String,
    val exportMyDataSubtitle: String,
    val deleteAllChats: String,
    val deleteAllChatsSubtitle: String,
    val deleteAccount: String,
    val deleteAccountSubtitle: String,
    val cancelButton: String,
    val saveButton: String,
    val deleteButton: String,
    val renameDialogTitle: String,
    val deleteDialogTitle: String,
    val deleteDialogBody: String,
    val shareDialogTitle: String,
    val shareDialogBody: String,
    val copyLinkButton: String,
    val shareButton: String,
    val revokeLinkButton: String,
    val closeButton: String,
    val copiedToClipboard: String,
    val linkCopiedToClipboard: String,
    val followingLabel: String,
    val followersLabel: String,
    val followAction: String,
    val followBackAction: String,
    val followingStatus: String,
    val unfollowAction: String,
    val unfollowConfirmation: String,
    val verifiedAccountDescription: String,
    val editProfileTitle: String,
    val bioLabel: String,
    val bioPlaceholder: String,
    val usernameReviewBannerTitle: String,
    val usernameReviewBannerSubtitle: String,
    val reviewButton: String,
    val keepCurrentButton: String,
    val usernameCooldownNotice: String,
    val usernameAvailable: String,
    val usernameUnavailable: String,
    val usernameInvalid: String,
    val discardChangesTitle: String,
    val discardChangesBody: String,
    val discardButton: String,
    val keepEditingButton: String,
    val spillTheTeaPrompt: String,
    val leaveANote: String,
    val notePlaceholder: String,
    val postNoteButton: String,
    val deleteNoteButton: String,
    val noteExpiresNotice: String,
    val searchScreenTitle: String,
    val peopleTab: String,
    val chatsTab: String,
    val recentSearches: String,
    val clearAll: String,
    val noPeopleFound: String,
    val noResultsFound: String,
    val searchError: String,
    val retryButton: String,
    val notificationsScreenTitle: String,
    val startedFollowingYou: String,
    val postedANote: String,
    val likedANote: String,
    val markAllAsRead: String,
    val userNotFoundTitle: String,
    val userNotFoundSubtitle: String,
    val shareProfileText: String,
    val copyAction: String,
    val editAction: String,
    val resendAction: String,
    val selectTextAction: String,
    val regenerateAction: String,
    val shareAction: String,
    val editedLabel: String,
    val editReplacesLaterHint: String,
    val sendButton: String,
    val selectTextTitle: String,
    val askBuildXAbout: String,
    val continueWithGoogle: String,
    val signUpButton: String,
    val logInButton: String,
    val usernameOrEmail: String,
    val createAccountHeading: String,
    val createAccountSubtitle: String,
    val welcomeBackHeading: String,
    val welcomeBackSubtitle: String,
    val requestVerificationTitle: String,
    val verificationStatusPending: String,
    val verificationStatusApproved: String,
    val verificationStatusRejected: String,
    val accountVerifiedDesc: String,
    val verificationUnderReview: String,
    val verificationReviewDesc: String,
    val verificationCriteriaHeading: String,
    val verificationCriteriaBody: String,
    val verificationFullNameLabel: String,
    val verificationCategoryLabel: String,
    val verificationLinksLabel: String,
    val verificationReasonLabel: String,
    val verificationSubmitButton: String,
    val verificationWithdrawButton: String,
    val withdrawConfirmTitle: String,
    val withdrawConfirmBody: String,
    val withdrawConfirmAction: String,
    val categoryTech: String,
    val categoryCreator: String,
    val categoryBusiness: String,
    val categoryEducation: String,
    val categoryMedia: String,
    val exampleCoding: String,
    val exampleIdeas: String,
    val exampleThinking: String,
    val exampleResearch: String,
    val exampleDrafting: String,
    val passwordLabel: String,
    val statementPlaceholder: String
)

val EnglishBuildXStrings = BuildXStrings(
    isRtl = false,
    appName = "Build X",
    askBuildX = "Ask Build X...",
    askPrivately = "Ask Build X privately...",
    replyPlaceholder = "Reply...",
    thinkingMode = "Thinking",
    instantMode = "Instant",
    instantDescription = "Answers straight away",
    thinkingDescription = "Reasons longer before answering",
    showThinking = "Show thinking",
    stepsTitle = "Steps",
    doneLabel = "Done",
    responseStopped = "Response stopped",
    incognitoSubtitle = "Incognito chat",
    incognitoEmptyNotice = "Temporary chats won't appear in your history and Build X won't save or remember anything you talk about.",
    searchPlaceholder = "Search",
    noMatchingConversations = "No matching conversations",
    newChat = "New chat",
    mediaTitle = "Media",
    chatsSection = "Chats",
    noConversationsYet = "Your conversations will appear here",
    notificationsTitle = "Notifications",
    turnOnNotificationsTitle = "Turn on notifications",
    turnOnNotificationsBody = "Get personalised recommendations, never miss a response and stay up to date on the latest features.",
    turnOnButton = "Turn on",
    noNotificationsTitle = "No notifications",
    noNotificationsSubtitle = "No notifications to show.",
    noMediaTitle = "No media yet",
    noMediaSubtitle = "Photos and files you attach in chats will appear here.",
    libraryTitle = "Library",
    seeAllButton = "See all",
    attachSelectedPrefix = "Attach",
    recentlyUploaded = "Recently uploaded",
    filesLabel = "Files",
    shareLink = "Share a link",
    pinChat = "Pin",
    unpinChat = "Unpin",
    renameChat = "Rename",
    deleteChat = "Delete chat",
    settingsTitle = "Settings",
    viewProfile = "View profile",
    appSettingsSection = "App settings",
    appAppearance = "App appearance",
    appearanceAutomatic = "Automatic",
    appearanceLight = "Light",
    appearanceDark = "Dark",
    appLanguage = "App language",
    languageSystem = "System default",
    languageEnglish = "English (United Kingdom)",
    languageArabic = "العربية (Arabic)",
    helpAndSupport = "Help & support",
    dataAndPrivacy = "Data & privacy",
    appInfo = "App info",
    appVersionLabel = "App version",
    buildNumberLabel = "Build number",
    legalAndSafety = "Legal & safety",
    logOut = "Log out",
    profileTitle = "Profile",
    changePhoto = "Change photo",
    displayNameLabel = "Display name",
    handleLabel = "Username",
    saveChanges = "Save changes",
    exportMyData = "Export my data",
    exportMyDataSubtitle = "Download your profile, conversations, and messages as JSON",
    deleteAllChats = "Delete all chats",
    deleteAllChatsSubtitle = "Permanently remove all saved conversations",
    deleteAccount = "Delete account",
    deleteAccountSubtitle = "Permanently delete your account and all associated data",
    cancelButton = "Cancel",
    saveButton = "Save",
    deleteButton = "Delete",
    renameDialogTitle = "Rename chat",
    deleteDialogTitle = "Delete chat?",
    deleteDialogBody = "This conversation and its messages will be permanently deleted.",
    shareDialogTitle = "Public read-only link",
    shareDialogBody = "Anyone with this link can view a read-only copy of this conversation until you revoke it.",
    copyLinkButton = "Copy link",
    shareButton = "Share",
    revokeLinkButton = "Revoke link",
    closeButton = "Close",
    copiedToClipboard = "Copied to clipboard",
    linkCopiedToClipboard = "Link copied to clipboard",
    followingLabel = "Following",
    followersLabel = "Followers",
    followAction = "Follow",
    followBackAction = "Follow back",
    followingStatus = "Following",
    unfollowAction = "Unfollow",
    unfollowConfirmation = "Unfollow @%s?",
    verifiedAccountDescription = "Verified account",
    editProfileTitle = "Edit profile",
    bioLabel = "Bio",
    bioPlaceholder = "Add a bio to your profile...",
    usernameReviewBannerTitle = "Review your username",
    usernameReviewBannerSubtitle = "Choose a permanent unique handle for your profile",
    reviewButton = "Review",
    keepCurrentButton = "Keep current",
    usernameCooldownNotice = "Usernames can only be changed once every 14 days",
    usernameAvailable = "Username is available",
    usernameUnavailable = "Username is already taken",
    usernameInvalid = "Only lowercase letters, numbers, dots and underscores (3-24 chars)",
    discardChangesTitle = "Discard changes?",
    discardChangesBody = "You have unsaved changes. Are you sure you want to discard them?",
    discardButton = "Discard",
    keepEditingButton = "Keep editing",
    spillTheTeaPrompt = "Spill the tea",
    leaveANote = "Leave a note",
    notePlaceholder = "Share what's on your mind...",
    postNoteButton = "Share",
    deleteNoteButton = "Delete note",
    noteExpiresNotice = "Your note is visible for 24 hours",
    searchScreenTitle = "Search",
    peopleTab = "People",
    chatsTab = "Chats",
    recentSearches = "Recent searches",
    clearAll = "Clear all",
    noPeopleFound = "No people found",
    noResultsFound = "No results found",
    searchError = "Something went wrong while searching",
    retryButton = "Retry",
    notificationsScreenTitle = "Notifications",
    startedFollowingYou = "started following you",
    postedANote = "posted a new note",
    likedANote = "liked your note",
    markAllAsRead = "Mark all as read",
    userNotFoundTitle = "User not found",
    userNotFoundSubtitle = "The profile you are looking for does not exist or has been removed.",
    shareProfileText = "Check out @%s on Build X: buildx://u/%s (Note: this link opens only for people who have the app installed)",
    copyAction = "Copy",
    editAction = "Edit",
    resendAction = "Resend",
    selectTextAction = "Select text",
    regenerateAction = "Regenerate",
    shareAction = "Share",
    editedLabel = "Edited",
    editReplacesLaterHint = "Later replies will be replaced",
    sendButton = "Send",
    selectTextTitle = "Select text",
    askBuildXAbout = "Ask Build X about",
    continueWithGoogle = "Continue with Google",
    signUpButton = "Sign up",
    logInButton = "Log in",
    usernameOrEmail = "Username or email",
    createAccountHeading = "Create account",
    createAccountSubtitle = "Choose your username and start chatting",
    welcomeBackHeading = "Log in",
    welcomeBackSubtitle = "Enter your username or email to continue",
    requestVerificationTitle = "Request Verification",
    verificationStatusPending = "Under Review",
    verificationStatusApproved = "Verified Account",
    verificationStatusRejected = "Request Rejected",
    accountVerifiedDesc = "This account has been verified as authentic and noteworthy.",
    verificationUnderReview = "Request Under Review",
    verificationReviewDesc = "Your request has been received and is being reviewed by our team. This usually takes 24-48 hours.",
    verificationCriteriaHeading = "Verification Criteria",
    verificationCriteriaBody = "The verified badge authenticates active public creators, developers, organizations, and verified contributors.",
    verificationFullNameLabel = "Full Name or Organization",
    verificationCategoryLabel = "Category",
    verificationLinksLabel = "Evidence / Verification Links",
    verificationReasonLabel = "Why should this account be verified?",
    verificationSubmitButton = "Submit Request",
    verificationWithdrawButton = "Withdraw Request",
    withdrawConfirmTitle = "Withdraw verification request?",
    withdrawConfirmBody = "Are you sure you want to withdraw your pending verification submission?",
    withdrawConfirmAction = "Withdraw",
    categoryTech = "Tech & Development",
    categoryCreator = "Content Creator",
    categoryBusiness = "Business & Brand",
    categoryEducation = "Education & Research",
    categoryMedia = "Media & News",
    exampleCoding = "writing code & building apps",
    exampleIdeas = "brainstorming creative ideas",
    exampleThinking = "deep analytical thinking",
    exampleResearch = "researching complex topics",
    exampleDrafting = "drafting and refining content",
    passwordLabel = "Password",
    statementPlaceholder = "Briefly explain why your account should be verified..."
)

val ArabicBuildXStrings = BuildXStrings(
    isRtl = true,
    appName = "Build X",
    askBuildX = "اسأل Build X...",
    askPrivately = "اسأل Build X بخصوصية...",
    replyPlaceholder = "رد...",
    thinkingMode = "Thinking",
    instantMode = "Instant",
    instantDescription = "يجيب مباشرة دون انتظار",
    thinkingDescription = "يفكر بعمق أكبر قبل الإجابة",
    showThinking = "عرض خطوات التفكير",
    stepsTitle = "خطوات التفكير",
    doneLabel = "اكتمل",
    responseStopped = "تم إيقاف الرد",
    incognitoSubtitle = "محادثة مخفية",
    incognitoEmptyNotice = "لن تظهر المحادثات المؤقتة في سجلك ولن يقوم Build X بحفظ أو تذكر أي شيء تتحدث عنه.",
    searchPlaceholder = "بحث",
    noMatchingConversations = "لا توجد محادثات مطابقة",
    newChat = "محادثة جديدة",
    mediaTitle = "الوسائط",
    chatsSection = "المحادثات",
    noConversationsYet = "ستظهر محادثاتك السابقة هنا",
    notificationsTitle = "الإشعارات",
    turnOnNotificationsTitle = "تفعيل الإشعارات",
    turnOnNotificationsBody = "احصل على تنبيهات فورية عند اكتمال الردود وابق على اطلاع بأحدث الميزات.",
    turnOnButton = "تفعيل",
    noNotificationsTitle = "لا توجد إشعارات",
    noNotificationsSubtitle = "ليس لديك أي إشعارات حاليا.",
    noMediaTitle = "لا توجد وسائط بعد",
    noMediaSubtitle = "الصور والملفات التي ترفقها في المحادثات ستظهر هنا.",
    libraryTitle = "المكتبة",
    seeAllButton = "عرض الكل",
    attachSelectedPrefix = "إرفاق",
    recentlyUploaded = "المرفوعة مؤخرا",
    filesLabel = "الملفات",
    shareLink = "مشاركة رابط",
    pinChat = "تثبيت",
    unpinChat = "إلغاء التثبيت",
    renameChat = "إعادة تسمية",
    deleteChat = "حذف المحادثة",
    settingsTitle = "الإعدادات",
    viewProfile = "عرض الملف الشخصي",
    appSettingsSection = "إعدادات التطبيق",
    appAppearance = "مظهر التطبيق",
    appearanceAutomatic = "تلقائي",
    appearanceLight = "فاتح",
    appearanceDark = "داكن",
    appLanguage = "لغة التطبيق",
    languageSystem = "لغة النظام الافتراضية",
    languageEnglish = "English (United Kingdom)",
    languageArabic = "العربية",
    helpAndSupport = "المساعدة والدعم",
    dataAndPrivacy = "البيانات والخصوصية",
    appInfo = "معلومات التطبيق",
    appVersionLabel = "إصدار التطبيق",
    buildNumberLabel = "رقم البناء",
    legalAndSafety = "الشروط والأمان",
    logOut = "تسجيل الخروج",
    profileTitle = "الملف الشخصي",
    changePhoto = "تغيير الصورة",
    displayNameLabel = "الاسم المعروض",
    handleLabel = "اسم المستخدم",
    saveChanges = "حفظ التغييرات",
    exportMyData = "تصدير بياناتي",
    exportMyDataSubtitle = "تنزيل ملفك الشخصي ومحادثاتك ورسائلك بصيغة JSON",
    deleteAllChats = "حذف جميع المحادثات",
    deleteAllChatsSubtitle = "إزالة كافة المحادثات المحفوظة نهائيا",
    deleteAccount = "حذف الحساب",
    deleteAccountSubtitle = "حذف حسابك وجميع البيانات المرتبطة به نهائيا",
    cancelButton = "إلغاء",
    saveButton = "حفظ",
    deleteButton = "حذف",
    renameDialogTitle = "إعادة تسمية المحادثة",
    deleteDialogTitle = "حذف المحادثة؟",
    deleteDialogBody = "سيتم حذف هذه المحادثة وجميع رسائلها نهائيا.",
    shareDialogTitle = "رابط مشاركة للقراءة فقط",
    shareDialogBody = "يمكن لأي شخص لديه هذا الرابط عرض نسخة للقراءة فقط من هذه المحادثة حتى تقوم بإلغائه.",
    copyLinkButton = "نسخ الرابط",
    shareButton = "مشاركة",
    revokeLinkButton = "إلغاء الرابط",
    closeButton = "إغلاق",
    copiedToClipboard = "تم النسخ إلى الحافظة",
    linkCopiedToClipboard = "تم نسخ الرابط إلى الحافظة",
    followingLabel = "المتابَعون",
    followersLabel = "المتابِعون",
    followAction = "متابعة",
    followBackAction = "رد المتابعة",
    followingStatus = "تتابعه",
    unfollowAction = "إلغاء المتابعة",
    unfollowConfirmation = "إلغاء متابعة @%s؟",
    verifiedAccountDescription = "حساب موثق",
    editProfileTitle = "تعديل الملف الشخصي",
    bioLabel = "النبذة التعريفية",
    bioPlaceholder = "أضف نبذة تعريفية إلى ملفك الشخصي...",
    usernameReviewBannerTitle = "راجع اسم المستخدم الخاص بك",
    usernameReviewBannerSubtitle = "اختر اسم مستخدم دائم ومميز لملفك الشخصي",
    reviewButton = "مراجعة",
    keepCurrentButton = "الإبقاء على الحالي",
    usernameCooldownNotice = "يمكن تغيير اسم المستخدم مرة واحدة كل 14 يوماً",
    usernameAvailable = "اسم المستخدم متاح",
    usernameUnavailable = "اسم المستخدم مأخوذ بالفعل",
    usernameInvalid = "فقط أحرف صغيرة وأرقام ونقاط وشرطات سفلية (3-24 حرف)",
    discardChangesTitle = "تجاهل التغييرات؟",
    discardChangesBody = "لديك تغييرات غير محفوظة. هل أنت متأكد من تجاهلها؟",
    discardButton = "تجاهل",
    keepEditingButton = "متابعة التعديل",
    spillTheTeaPrompt = "ما الجديد لديك؟",
    leaveANote = "أضف ملاحظة",
    notePlaceholder = "شارك ما يدور في ذهنك...",
    postNoteButton = "مشاركة",
    deleteNoteButton = "حذف الملاحظة",
    noteExpiresNotice = "ستبقى ملاحظتك ظاهرة لمدة 24 ساعة",
    searchScreenTitle = "بحث",
    peopleTab = "الأشخاص",
    chatsTab = "المحادثات",
    recentSearches = "عمليات البحث الأخيرة",
    clearAll = "مسح الكل",
    noPeopleFound = "لم يتم العثور على أشخاص",
    noResultsFound = "لم يتم العثور على نتائج",
    searchError = "حدث خطأ أثناء البحث",
    retryButton = "إعادة المحاولة",
    notificationsScreenTitle = "الإشعارات",
    startedFollowingYou = "بدأ بمتابعتك",
    postedANote = "نشر ملاحظة جديدة",
    likedANote = "أعجب بملاحظتك",
    markAllAsRead = "تحديد الكل كمقروء",
    userNotFoundTitle = "المستخدم غير موجود",
    userNotFoundSubtitle = "الملف الشخصي الذي تبحث عنه غير موجود أو تمت إزالته.",
    shareProfileText = "تفقد @%s على Build X: buildx://u/%s (ملاحظة: هذا الرابط يفتح فقط لمن قام بتثبيت التطبيق)",
    copyAction = "نسخ",
    editAction = "تعديل",
    resendAction = "إعادة الإرسال",
    selectTextAction = "تحديد النص",
    regenerateAction = "إعادة الإنشاء",
    shareAction = "مشاركة",
    editedLabel = "معدلة",
    editReplacesLaterHint = "سيتم استبدال الردود اللاحقة",
    sendButton = "إرسال",
    selectTextTitle = "تحديد النص",
    askBuildXAbout = "اسأل Build X عن",
    continueWithGoogle = "المتابعة باستخدام Google",
    signUpButton = "إنشاء حساب",
    logInButton = "تسجيل الدخول",
    usernameOrEmail = "اسم المستخدم أو البريد الإلكتروني",
    createAccountHeading = "إنشاء حساب",
    createAccountSubtitle = "اختر اسم المستخدم الفريد الخاص بك وابدأ المحادثات",
    welcomeBackHeading = "تسجيل الدخول",
    welcomeBackSubtitle = "أدخل اسم المستخدم أو البريد الإلكتروني للمتابعة",
    requestVerificationTitle = "طلب التوثيق",
    verificationStatusPending = "قيد المراجعة",
    verificationStatusApproved = "حساب موثق",
    verificationStatusRejected = "تم رفض الطلب",
    accountVerifiedDesc = "تم توثيق هذا الحساب رسمياً والتأكد من هويته وموثوقيته.",
    verificationUnderReview = "الطلب قيد المراجعة",
    verificationReviewDesc = "تم استلام طلب التوثيق وهو قيد المراجعة والتدقيق من قبل الفريق المختص.",
    verificationCriteriaHeading = "معايير التوثيق",
    verificationCriteriaBody = "تؤكد شارة التوثيق هوية المطورين وصناع المحتوى والجهات المعتمدة ذات الحضور النشط والموثوق.",
    verificationFullNameLabel = "الاسم الكامل أو اسم الجهة",
    verificationCategoryLabel = "التصنيف",
    verificationLinksLabel = "روابط أو أدلة موثوقة",
    verificationReasonLabel = "لماذا يجب توثيق هذا الحساب؟",
    verificationSubmitButton = "إرسال طلب التوثيق",
    verificationWithdrawButton = "سحب الطلب",
    withdrawConfirmTitle = "سحب طلب التوثيق؟",
    withdrawConfirmBody = "هل أنت متأكد من رغبتك في سحب طلب التوثيق المعلق؟",
    withdrawConfirmAction = "سحب الطلب",
    categoryTech = "التطوير والبرمجة",
    categoryCreator = "صناعة المحتوى",
    categoryBusiness = "الأعمال والعلامات التجارية",
    categoryEducation = "التعليم والأبحاث",
    categoryMedia = "الإعلام والصحافة",
    exampleCoding = "كتابة الأكواد البرمجية",
    exampleIdeas = "توليد الأفكار الإبداعية",
    exampleThinking = "التفكير والتحليل العميق",
    exampleResearch = "البحث والمعرفة الدقيقة",
    exampleDrafting = "صياغة وتلخيص المحتوى",
    passwordLabel = "كلمة المرور",
    statementPlaceholder = "اشرح بإيجاز سبب طلب التوثيق..."
)

val LocalBuildXColors = staticCompositionLocalOf { LightBuildXColors }
val LocalBuildXStrings = staticCompositionLocalOf { EnglishBuildXStrings }

/**
 * Bundled open-license fonts: Plus Jakarta Sans (Latin geometric grotesque) + Noto Sans Arabic.
 */
val BuildXFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.noto_sans_arabic, FontWeight.Normal),
    Font(R.font.noto_sans_arabic, FontWeight.Medium),
    Font(R.font.noto_sans_arabic, FontWeight.SemiBold),
    Font(R.font.noto_sans_arabic, FontWeight.Bold)
)

val BuildXTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BuildXFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
)

object BuildXTheme {
    val colors: BuildXColors
        @Composable
        get() = LocalBuildXColors.current

    val strings: BuildXStrings
        @Composable
        get() = LocalBuildXStrings.current
}

@Composable
fun BuildXTheme(
    appearance: AppAppearance = AppAppearance.AUTOMATIC,
    language: AppLanguage = AppLanguage.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (appearance) {
        AppAppearance.AUTOMATIC -> systemDark
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }

    val configuration = LocalConfiguration.current
    val systemLocale = if (configuration.locales.isEmpty) {
        Locale.getDefault()
    } else {
        configuration.locales[0]
    }
    val useArabic = when (language) {
        AppLanguage.ARABIC -> true
        AppLanguage.ENGLISH -> false
        AppLanguage.SYSTEM -> systemLocale.language.lowercase().startsWith("ar")
    }

    val buildXColors = if (useDark) DarkBuildXColors else LightBuildXColors
    val buildXStrings = if (useArabic) ArabicBuildXStrings else EnglishBuildXStrings
    val layoutDirection = if (useArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    val materialColorScheme = if (useDark) {
        darkColorScheme(
            primary = buildXColors.primaryBlue,
            onPrimary = buildXColors.onPrimaryBlue,
            background = buildXColors.pageBackground,
            onBackground = buildXColors.primaryText,
            surface = buildXColors.cardBackground,
            onSurface = buildXColors.primaryText,
            surfaceVariant = buildXColors.surfaceMuted,
            onSurfaceVariant = buildXColors.secondaryText,
            error = buildXColors.destructiveRed
        )
    } else {
        lightColorScheme(
            primary = buildXColors.primaryBlue,
            onPrimary = buildXColors.onPrimaryBlue,
            background = buildXColors.pageBackground,
            onBackground = buildXColors.primaryText,
            surface = buildXColors.cardBackground,
            onSurface = buildXColors.primaryText,
            surfaceVariant = buildXColors.surfaceMuted,
            onSurfaceVariant = buildXColors.secondaryText,
            error = buildXColors.destructiveRed
        )
    }

    CompositionLocalProvider(
        LocalBuildXColors provides buildXColors,
        LocalBuildXStrings provides buildXStrings,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = BuildXTypography,
            content = content
        )
    }
}

/**
 * Smooth Gaussian shadow modifier using [BlurMaskFilter].
 * Eliminates stepped concentric `drawRoundRect` banding while working cleanly across
 * hardware-accelerated Android canvases and Robolectric Native Graphics.
 */
fun Modifier.softShadow(
    color: Color,
    cornerRadius: Dp,
    blurRadius: Dp,
    offsetY: Dp = 4.dp,
    offsetX: Dp = 0.dp,
    spread: Dp = 0.dp
): Modifier = this.drawWithCache {
    val blurPx = blurRadius.toPx()
    val cornerPx = cornerRadius.toPx()
    val spreadPx = spread.toPx()
    val dx = offsetX.toPx()
    val dy = offsetY.toPx()

    val frameworkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        if (blurPx > 0.5f) {
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
    }

    onDrawBehind {
        if (color.alpha <= 0.002f || size.width <= 0f || size.height <= 0f) {
            return@onDrawBehind
        }
        frameworkPaint.color = color.toArgb()
        val left = -spreadPx + dx
        val top = -spreadPx + dy
        val right = size.width + spreadPx + dx
        val bottom = size.height + spreadPx + dy
        val r = (cornerPx + spreadPx).coerceAtLeast(0f)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                left,
                top,
                right,
                bottom,
                r,
                r,
                frameworkPaint
            )
        }
    }
}

/**
 * Resolves text direction from the first strong directional character in [text].
 * If [text] has no strong characters yet (e.g. empty or punctuation only), falls back to [defaultRtl].
 */
fun isRtlText(text: String, defaultRtl: Boolean = false): Boolean {
    for (ch in text) {
        val dir = Character.getDirectionality(ch)
        when (dir) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> return true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> return false
        }
    }
    return defaultRtl
}
