# F-Droid submission

`ru.stohastika.sklyanka.yml` is the metadata file the F-Droid build repository expects. It is
kept here as the source of truth; the file submitted to
[`fdroid/fdroiddata`](https://gitlab.com/fdroid/fdroiddata) is byte for byte this one.

The file has no comments on purpose: `fdroid rewritemeta` strips comments, and the F-Droid
pipeline fails when the normalized file differs from the submitted one.

Checking it locally:

```bash
uv venv /tmp/fdvenv && uv pip install --python /tmp/fdvenv/bin/python fdroidserver check-jsonschema
git clone --depth 1 --filter=blob:none --sparse https://gitlab.com/fdroid/fdroiddata.git /tmp/fdroiddata
cd /tmp/fdroiddata && git sparse-checkout set metadata config schemas tools
cp <this file> metadata/ru.stohastika.sklyanka.yml
/tmp/fdvenv/bin/fdroid rewritemeta ru.stohastika.sklyanka && git diff --exit-code
/tmp/fdvenv/bin/fdroid lint ru.stohastika.sklyanka
/tmp/fdvenv/bin/fdroid checkupdates --auto -v ru.stohastika.sklyanka && git diff --exit-code
/tmp/fdvenv/bin/check-jsonschema --schemafile schemas/metadata.json metadata/*.yml
```

Notes that the metadata encodes:

- `Categories: Medication`: the F-Droid taxonomy has no "Health and Fitness".
- `gradle: - free` builds `assembleFreeRelease`. A release build without signing secrets stays
  unsigned, which is what the build server needs; it signs the artifact with the F-Droid key.
- `output:` names the unsigned artifact.
- `UpdateCheckData` reads `versionCode` and `versionName` from the Gradle convention plugin, where
  the project keeps them; without it `checkupdates` cannot derive a version from the tags.
- `UpdateCheckMode: Tags` with `AutoUpdateMode: Version`: a release is a tag, and the tag is
  `v<versionName>`.
