pipeline {
    agent {
        label 'java17'
    }

    options {
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(
            numToKeepStr: '20',
            artifactNumToKeepStr: '10'
        ))
    }

    environment {
        // Maven
        MAVEN_SETTINGS = '/var/jenkins_home/.m2/settings.xml'
        REPO_ID       = 'snapshots'
        REPO_URL      = "http://${env.MAVEN_URL}/repository/maven-snapshots/"

        // 项目目录
        PROJECT_NAME = "${env.JOB_BASE_NAME}"
        DIR_API      = "${env.DIR_KEY}-api"
        DIR_SERVICE  = "${env.DIR_KEY}-service"

        // Docker/TCR
        TCR_REGISTRY = 'ccr.ccs.tencentyun.com'
        IMAGE_NAME   = "${env.REGISTRY_URL}/${env.JOB_BASE_NAME}:${env.BUILD_NUMBER}"

        // 启动检查
        STARTUP_MAX_RETRIES = '15'
        STARTUP_INTERVAL    = '5'
    }

    parameters {
        booleanParam(
            name: 'DEPLOY_API',
            defaultValue: false,
            description: '是否构建并发布 API'
        )

        booleanParam(
            name: 'DOCKER_NO_CACHE',
            defaultValue: false,
            description: '构建镜像时是否禁用缓存'
        )
    }

    tools {
        jdk   'temurin-17'
        maven 'M3'
    }

    stages {
        stage('环境检查') {
            steps {
                sh '''
                    set -eu

                    echo '============================== 环境检查 =============================='

                    echo "Jenkins 用户：$(whoami)"
                    echo "项目名称：${PROJECT_NAME}"
                    echo "API 目录：${DIR_API}"
                    echo "Service 目录：${DIR_SERVICE}"
                    echo "镜像名称：${IMAGE_NAME}"

                    java -version
                    mvn -version
                    docker version

                    test -f "${MAVEN_SETTINGS}"
                    test -d "${DIR_SERVICE}"
                    test -f "${DIR_SERVICE}/pom.xml"

                    docker network inspect openiov-management >/dev/null
                '''
            }
        }

        stage('构建并发布 API') {
            when {
                expression {
                    return params.DEPLOY_API
                }
            }

            steps {
                dir("${DIR_API}") {
                    sh '''
                        set -eu

                        echo '============================== 构建并发布 API =============================='

                        mvn \
                            --settings "${MAVEN_SETTINGS}" \
                            --batch-mode \
                            --update-snapshots \
                            clean deploy \
                            -DskipTests \
                            -DaltDeploymentRepository="${REPO_ID}::default::${REPO_URL}"
                    '''
                }
            }
        }

        stage('构建服务镜像') {
            steps {
                script {
                    def noCacheArg = params.DOCKER_NO_CACHE ? '--no-cache' : ''

                    sh """
                        set -eu

                        echo '============================== 构建服务镜像 =============================='

                        SETTINGS_TARGET='${DIR_SERVICE}/settings.xml'
                        MAVEN_TARGET='${DIR_SERVICE}/apache-maven-3.6.3-bin.tar.gz'

                        cleanup_build_files() {
                            rm -f "\${SETTINGS_TARGET}" "\${MAVEN_TARGET}"
                        }

                        trap cleanup_build_files EXIT

                        cp '${MAVEN_SETTINGS}' "\${SETTINGS_TARGET}"
                        cp '/var/jenkins_home/apache-maven-3.6.3-bin.tar.gz' "\${MAVEN_TARGET}"

                        docker build \
                            --network openiov-management \
                            ${noCacheArg} \
                            --tag '${IMAGE_NAME}' \
                            --file '/var/jenkins_home/ServiceDockerfile' \
                            '${DIR_SERVICE}'

                        docker image inspect '${IMAGE_NAME}' >/dev/null
                    """
                }
            }
        }

        stage('上传服务镜像') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'tencent-tcr',
                        usernameVariable: 'TCR_USERNAME',
                        passwordVariable: 'TCR_PASSWORD'
                    )
                ]) {
                    sh '''
                        set -eu

                        echo '============================== 上传服务镜像 =============================='

                        logout_tcr() {
                            docker logout "${TCR_REGISTRY}" >/dev/null 2>&1 || true
                        }

                        trap logout_tcr EXIT

                        echo "${TCR_PASSWORD}" |
                            docker login \
                                --username "${TCR_USERNAME}" \
                                --password-stdin \
                                "${TCR_REGISTRY}"

                        docker push "${IMAGE_NAME}"
                    '''
                }
            }
        }

        stage('部署服务') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'tencent-tcr',
                        usernameVariable: 'TCR_USERNAME',
                        passwordVariable: 'TCR_PASSWORD'
                    )
                ]) {
                    sh '''
                        set -eu

                        echo '============================== 部署服务 =============================='

                        ROLLBACK_CONTAINER="${PROJECT_NAME}-rollback"
                        ROLLBACK_AVAILABLE=0

                        logout_tcr() {
                            docker logout "${TCR_REGISTRY}" >/dev/null 2>&1 || true
                        }

                        rollback() {
                            echo '============================== 部署回滚 =============================='

                            echo "删除启动失败的新容器：${PROJECT_NAME}"
                            docker rm -f "${PROJECT_NAME}" >/dev/null 2>&1 || true

                            if [ "${ROLLBACK_AVAILABLE}" -eq 1 ]; then
                                echo "恢复旧容器：${PROJECT_NAME}"

                                docker rename \
                                    "${ROLLBACK_CONTAINER}" \
                                    "${PROJECT_NAME}"

                                docker start "${PROJECT_NAME}"
                            else
                                echo '没有可用于回滚的旧容器。'
                            fi
                        }

                        deployment_failed() {
                            echo '============================== 部署失败 =============================='
                            echo '新容器最后 500 行日志：'

                            docker logs \
                                --tail 500 \
                                "${PROJECT_NAME}" 2>&1 || true

                            rollback
                            exit 1
                        }

                        trap logout_tcr EXIT

                        # 私有仓库 pull 前必须登录
                        echo "${TCR_PASSWORD}" |
                            docker login \
                                --username "${TCR_USERNAME}" \
                                --password-stdin \
                                "${TCR_REGISTRY}"

                        # 先拉取成功，再停止旧容器
                        echo "拉取镜像：${IMAGE_NAME}"
                        docker pull "${IMAGE_NAME}"

                        # 清理上一次可能遗留的回滚容器
                        if docker container inspect \
                            "${ROLLBACK_CONTAINER}" >/dev/null 2>&1; then

                            echo "清理遗留回滚容器：${ROLLBACK_CONTAINER}"
                            docker rm -f "${ROLLBACK_CONTAINER}"
                        fi

                        # 保留旧容器用于回滚
                        if docker container inspect \
                            "${PROJECT_NAME}" >/dev/null 2>&1; then

                            echo "停止旧容器：${PROJECT_NAME}"
                            docker stop "${PROJECT_NAME}"

                            echo "保留旧容器用于回滚：${ROLLBACK_CONTAINER}"
                            docker rename \
                                "${PROJECT_NAME}" \
                                "${ROLLBACK_CONTAINER}"

                            ROLLBACK_AVAILABLE=1
                        else
                            echo '未发现旧容器，本次为首次部署。'
                        fi

                        # 启动新容器
                        echo "启动新容器：${PROJECT_NAME}"

                        if ! docker run \
                            --detach \
                            --name "${PROJECT_NAME}" \
                            --network openiov-management \
                            --restart unless-stopped \
                            --env-file /var/jenkins_home/nacos.env \
                            "${IMAGE_NAME}"; then

                            echo '新容器创建失败。'
                            rollback
                            exit 1
                        fi

                        echo '开始检查服务启动状态……'

                        count=1
                        SUCCESS=0

                        while [ "${count}" -le "${STARTUP_MAX_RETRIES}" ]
                        do
                            echo "检查服务状态：${count}/${STARTUP_MAX_RETRIES}"

                            # 检查容器是否仍在运行
                            if [ -z "$(docker ps \
                                --quiet \
                                --filter "name=^/${PROJECT_NAME}$")" ]; then

                                echo '容器已经退出。'
                                deployment_failed
                            fi

                            # 检查 Spring Boot 启动日志
                            if docker logs "${PROJECT_NAME}" 2>&1 |
                                grep -Eiq 'Started .+ in .+ seconds'; then

                                SUCCESS=1
                                break
                            fi

                            count=$((count + 1))
                            sleep "${STARTUP_INTERVAL}"
                        done

                        if [ "${SUCCESS}" -ne 1 ]; then
                            echo '服务未在规定时间内完成启动。'
                            deployment_failed
                        fi

                        echo '============================== 部署成功 =============================='
                        docker ps \
                            --filter "name=^/${PROJECT_NAME}$" \
                            --format '容器={{.Names}} 镜像={{.Image}} 状态={{.Status}}'

                        # 新服务成功后再删除旧容器
                        if [ "${ROLLBACK_AVAILABLE}" -eq 1 ]; then
                            echo "删除旧容器：${ROLLBACK_CONTAINER}"
                            docker rm -f "${ROLLBACK_CONTAINER}"
                        fi
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "部署成功：${IMAGE_NAME}"
        }

        failure {
            echo "流水线执行失败：${env.BUILD_URL}"
        }

        always {
            sh '''
                docker logout ccr.ccs.tencentyun.com \
                    >/dev/null 2>&1 || true
            '''
        }
    }
}