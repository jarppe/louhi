FROM debian:12-slim


ENV LANG=C.UTF-8
WORKDIR /app


#
# Base deps:
#


RUN \
  apt update -q                                                                    && \
  apt upgrade -y                                                                   && \
  apt install -y                                                                   \
    ca-certificates                                                                \
    apt-transport-https                                                            \
    lsb-release-minimal                                                            \
    curl                                                                           \
    gnupg


#
# Java:
#


ARG JAVA_VERSION=22

RUN \
  curl -sSL https://packages.adoptium.net/artifactory/api/gpg/key/public           \
    | gpg --dearmor                                                                \
    > /etc/apt/trusted.gpg.d/temurin.gpg                                           && \
  echo "deb https://packages.adoptium.net/artifactory/deb"                         \
            $(lsb_release -cs)                                                     \
            "main"                                                                 \
    > /etc/apt/sources.list.d/adoptium.list                                        && \
  apt update -q                                                                    && \
  apt install -y                                                                   \
    temurin-${JAVA_VERSION}-jdk


#
# Clojure:
#


RUN \
  apt install -y                                                                   \
    jq


RUN \
  RELEASE=$(curl -sSLf "https://api.github.com/repos/clojure/brew-install/releases/latest" | jq -r ".tag_name") && \
  curl -sSLf https://github.com/clojure/brew-install/releases/download/${RELEASE}/posix-install.sh \
    | bash -                                                                       && \
  clojure -P                                                                       && \
  clojure --version


#
# Babashka:
#


RUN \
  RELEASE=$(curl -sSLf "https://api.github.com/repos/babashka/babashka/releases/latest" | jq -r ".tag_name[1:]") && \
  case $(uname -m) in                                                              \
    aarch64) ARCH=aarch64;;                                                        \
    x86_64)  ARCH=amd64;;                                                          \
    *) echo "Unknown CPU: $(uname -m)"; exit 1;;                                   \
  esac                                                                             && \
  BB_TAR="babashka-${RELEASE}-linux-${ARCH}-static.tar.gz"                         && \
  curl -sSL "https://github.com/babashka/babashka/releases/download/v${RELEASE}/${BB_TAR}" \
    | tar xzCf /usr/local/bin -                                                    && \
  bb --version


#
# Bun
#


RUN \
  apt install -y                                                                   \
    unzip


RUN \
  curl -fsSL https://bun.sh/install | bash


#
# PostCSS
#


COPY \
  package.json postcss.config.js ./

RUN \
  /root/.bun/bin/bun install

ENV BUN_INSTALL=/root/.bun
ENV PATH=/root/.bun/bin:$PATH


#
# Misc:
#   Add misc deps that come and go here. The steps above are a bit heavy, so
#   updating these deps in here avoids rebuilding the layers above. Eventually
#   stable deps from these can be moved to top.
#


RUN \
  apt install -y                                                                   \
    git                                                                            \
    binutils                                                                       \
    rlwrap                                                                         \
    procps                                                                         \
    inetutils-ping                                                                 \
    socat                                                                          \
    httpie                                                                         \
    librsvg2-bin                                                                   \
    imagemagick                                                                    \
    woff2


#
# Home etc:
#


ENV HOME=/root

COPY ./.bashrc                                                                     \
     /root/

CMD ["/bin/bash"]


#
# Project setup:
#   When started dev containers will mount project root to /app overriding
#   these files. How ever, the deps.edn is used during the build to load 
#   dependencies into the image (to speed up repl start time) and the
#   bash config is handy to have in image so that this image can be used 
#   as regular tool image. 
#


COPY ./deps.edn                                                                    \
     /app/

RUN ["clojure", "-A:dev:test:calva", "-P"]

