FROM gcr.io/oss-fuzz-base/base-builder-jvm@sha256:e3ac22ef17541ca88d404971801c4d8191860f8bf5429f59ebd229a4e6c42cd4

COPY . $SRC/rsql-jpa-search
WORKDIR $SRC/rsql-jpa-search
COPY .github/clusterfuzzlite/build.sh $SRC/build.sh
RUN chmod +x "$SRC/build.sh"
