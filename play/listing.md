# Google Play: what to enter in Play Console

The legal declarations are yours: read each answer before you submit it.

## Upload

- File: the Play bundle, built with `./gradlew bundleRelease -PplayStore=true`
  (no Ko-fi link: Google Play does not allow links to payments outside
  Google Play). Upload key: `Desktop\sbm-android-signing` (see its README).
- Use Play App Signing (the default).
- Personal developer accounts made after 13 November 2023 must run a closed
  test with at least 12 testers for 14 days in a row before production.

## Store listing

- App name: `sbm bookmarks`
- Short and full description: `fastlane/metadata/android/en-US/`
- App icon (512 px), feature graphic (1024x500), phone screenshots:
  `fastlane/metadata/android/en-US/images/`. The screenshots show demo
  bookmarks only.
- Category: Productivity
- Contact email: truex@equwal.com
- Website: https://github.com/equwal/sbm-android
- Privacy policy: https://sbm.subread.space/privacy

## App content

- **Privacy policy:** https://sbm.subread.space/privacy
- **Ads:** no ads.
- **App access:** some functions need an account (sync). Give the review
  account in `Desktop\sbm-android-signing\play-review-account.txt`, with
  these steps: "Open the menu, tap Sign in to sync. The server is filled in.
  Enter the email and the password, tap Sign in."
- **Content rating** (IARC questionnaire), category "Utility, Productivity,
  Communication, or Other": no violence, no sexual content, no bad
  language, no drugs, no gambling; users do not talk to other users or
  share content with other users; no sharing of location; no purchases of
  digital goods. Expected result: Everyone / PEGI 3.
- **Target audience:** 18 and over.
- **News app:** no. **Government app:** no. **Financial features:** none.
  **Health:** no.
- **Data safety:**
  - Does the app collect or share user data? Yes, it collects; it shares
    nothing.
  - Is all data encrypted in transit? Yes (HTTPS).
  - Can users ask for deletion of their data? Yes:
    https://sbm.subread.space/privacy#delete
  - Personal info, email address: collected, optional, for account
    management. Not shared.
  - App activity, other user-generated content (the bookmarks): collected,
    optional, for app functionality. Not shared.
  - No location, no contacts, no device IDs, no crash logs, no analytics.
  - Data is collected only when the user signs in to sync.
- **Account deletion:** users make accounts on the website, not in the app.
  Deletion URL: https://sbm.subread.space/privacy#delete

## Pricing

Free. No in-app purchases. The app does not link to the paid plan of
sbm.subread.space.
