pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-25-amazon-corretto.x86_64'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
    }

    stages {
        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }

        stage('Package') {
            steps {
                sh 'mvn package -DskipTests'
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    cp target/*.jar /deploy_jenkins/dev/orderflow.jar
                    sudo systemctl restart orderflow_dev.service
                '''
            }
        }

        stage('Approval') {
            steps {
                input message: 'Dev verified. Deploy to staging?'
            }
        }

        stage('Deploy Staging') {
            steps {
                sh '''
                    cp target/*.jar /deploy_jenkins/stage/orderflow.jar
                    sudo systemctl restart orderflow_stage.service
                '''
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
        }
    }
}
