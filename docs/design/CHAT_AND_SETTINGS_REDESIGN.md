# Chat and Settings Redesign

## Product intent

Direct messages are for maintaining a relationship, so the thread must make the
conversation itself the dominant object. Settings are for making a deliberate
choice, so the screen must make categories and consequences clear before a
person taps anything. Both surfaces use the same Build X language: a calm page
surface, elevated white cards only for controls or durable context, soft
contours, and blue reserved for an action or state that needs attention.

## Conversation architecture

1. The floating header is a compact identity and action bar. It exposes the
   person or group, opens the complete information sheet, and keeps secondary
   actions in the overflow menu.
2. A pinned message is contextual, not a second header. It is a short, tappable
   visual strip with a single-line preview and a distinct unpin action.
3. The timeline carries the reading burden. Day dividers establish orientation,
   group chats identify non-local senders, and every completed message exposes
   a readable send time. Deleted, edited, reply, and media states remain
   visually subordinate to message content.
4. The composer is an input dock rather than a toolbar. Its shape expands with
   reply/edit context and attachments; its primary action changes from voice to
   send only when there is actual content. The attachment control disappears
   while typing, preserving writing space.

## Attachment behavior

The attachment sheet supports a real decision flow instead of decorative
destinations: select one or more recent photos and confirm them together,
choose additional photos from the system picker, take a photo after Android
camera permission, or select a document. Every path returns a staged
attachment to the DM composer before sending.

## Settings architecture

Settings are divided into four mental models rather than one exhaustive card:

- **Account**: identity and profile entry point.
- **Experience**: notifications, appearance, and language.
- **Support & safety**: support, privacy controls, blocked accounts, and legal
  information.
- **Build X**: verification state and version information.

Destructive sign-out stays visually and spatially separate. This keeps routine
preferences easy to scan while preventing high-impact actions from blending
into ordinary navigation. Every destination remains wired to its existing
navigation or state handler and continues to respect the selected RTL/LTR
layout and light/dark theme.
