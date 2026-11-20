# 젠킨스 컨테이너 이미지: 공식 LTS에 docker CLI와 kubectl만 더한다 (ADR-037).
# docker 데몬은 호스트 것을 쓰므로 실행할 때 /var/run/docker.sock을 마운트한다 (README).
# VM(arm64) 기준이다. 버전은 k3s 서버 버전에 맞춰 바꾼다(kubectl은 서버와 ±1 마이너까지 호환).
FROM jenkins/jenkins:lts-jdk21
USER root
ARG KUBECTL_VERSION=v1.33.5
ARG DOCKER_VERSION=27.5.1
ARG TARGETARCH
RUN set -eu; \
    case "${TARGETARCH:-arm64}" in arm64) DARCH=aarch64 ;; amd64) DARCH=x86_64 ;; esac; \
    curl -fsSL "https://download.docker.com/linux/static/stable/${DARCH}/docker-${DOCKER_VERSION}.tgz" \
      | tar -xz --strip-components=1 -C /usr/local/bin docker/docker; \
    curl -fsSLo /usr/local/bin/kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${TARGETARCH:-arm64}/kubectl"; \
    curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${TARGETARCH:-arm64}/kubectl.sha256" -o /tmp/kubectl.sha256; \
    echo "$(cat /tmp/kubectl.sha256)  /usr/local/bin/kubectl" | sha256sum -c -; \
    chmod +x /usr/local/bin/kubectl; rm /tmp/kubectl.sha256
USER jenkins
