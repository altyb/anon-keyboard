# Store submissions

**IzzyOnDroid:** not eligible. Its [inclusion policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/) rejects apps whose code was written with generative AI.

**F-Droid:** not before 2027-01-29 (Quality point 6: 3 months of active development unless the app has proven users; disclosing AI assistance is encouraged). Then a merge request to https://gitlab.com/fdroid/fdroiddata adding `metadata/dev.altayeb.hudhud.yml`. Builds with `./gradlew assembleRelease` and only Android SDK tools.

**Google Play:** the Contacts permission needs a declaration once the app targets API 37. Privacy policy: https://github.com/altyb/hudhud/blob/main/PRIVACY.md
