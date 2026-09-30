# K-Pulse Field — publishing checklist

Answers for the **Play Console → Policy → App content** forms, plus what must be
fixed before Google's review can pass. Verify each answer against the app as it
ships; Google holds you to what you declare.

## Must fix before submitting

These will get the app rejected or make it impossible for Google to review.

1. **Reviewers cannot sign in.** Google requires working access to every restricted
   screen (*App content → App access*). Production has no SMS gateway, so no one can
   receive a sign-in code, and a fresh production database has no agents. Needed: SMS
   delivery for OTPs (2Factor is already used in your distributor project) and a
   reviewer agent account with approved access to a demo booth, plus instructions for
   the reviewer.
2. **Account deletion.** The app lets agents create an account (sign up), so Google
   requires a way to request account deletion **inside the app** and at a **web URL**.
   The privacy policy describes an email route; the app still needs a visible
   "Delete account" option, and the web URL should point to that section.
3. **Signed App Bundle.** Play only accepts a signed `.aab`. The current release build is
   an unsigned APK. Create an upload key, add a release signing config (keep the
   keystore and its passwords out of git), then run `./gradlew bundleRelease`.
4. **Privacy policy placeholders.** Fill in every highlighted `[...]` in
   `frontend/public/privacy.html` (organisation, address, contact, retention, grievance
   officer), rebuild the frontend and deploy it so
   `https://direco.co.in/kpulse/privacy.html` is live. Have it reviewed by someone
   qualified: the app processes political opinions tied to named voters.
5. **Personal developer accounts** created after November 2023 must run a **closed test
   with at least 12 testers for 14 continuous days** before production access is
   granted. Organisation accounts are exempt.

## Review risks to be aware of

- **WebView-only apps.** Play's minimum functionality policy rejects apps that are just a
  website in a wrapper. K-Pulse Field is a restricted-access tool rather than a public
  site, which helps, but describe that clearly in the App access notes.
- **Political data.** Voter sentiment is sensitive personal data. Declare it accurately in
  Data safety (below) and keep the "not affiliated with the Election Commission" line in
  the description so the listing cannot be read as an official app.

## App content answers

### Privacy policy
`https://direco.co.in/kpulse/privacy.html`

### Ads
No, the app does not contain ads.

### App access
All or some functionality is restricted. Provide the reviewer agent's mobile number, how
they receive the one-time code, and which booth they can open. (Blocked by item 1.)

### Content rating (IARC questionnaire)
Category **Reference, News, or Educational** is not a fit; choose **Utility, Productivity,
Communication, or Other**. Answer **No** to violence, sexuality, profanity, drugs, gambling
and user-to-user communication. Answer **No** to sharing the user's location. Expected
rating: Everyone / PEGI 3 equivalent.

### Target audience
18 and over only. The app is not designed for children.

### News app
No.

### Government app
No. Developed independently, not on behalf of a government.

### Financial features / Health
None.

### Data safety

| Question | Answer |
|---|---|
| Does the app collect or share user data? | Yes, collects. No sharing with third parties beyond service providers. |
| Is all data encrypted in transit? | Yes (HTTPS) |
| Can users request that data is deleted? | Yes (after item 2 is built) |

Data types collected, all **not optional** unless noted, **not shared**, and used for
**App functionality** and **Account management** unless noted:

| Category → type | Collected | Notes |
|---|---|---|
| Personal info → Name | Yes | Agent account; also voter names entered or viewed |
| Personal info → Email address | Yes | Agent account |
| Personal info → Phone number | Yes | Agent sign-in |
| Personal info → Address | Yes | Agent account |
| Personal info → Political or religious beliefs | Yes | Voter sentiment toward a candidate. Purpose: App functionality |
| Personal info → Other info | Yes | Voter age, gender, relation, house number, EPIC number |
| App activity → Other user-generated content | Yes | Sentiment records and proposed roll changes |
| Location, Contacts, Photos, Financial info, Device IDs | No | Not collected |

Processing is not ephemeral: records are stored on the server.
