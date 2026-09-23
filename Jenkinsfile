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

//         stage('Approval') {
//             steps {
//                 input message: 'Dev verified. Deploy to staging?'
//             }
//         }
//
//         stage('Deploy Staging') {
//             steps {
//                 sh '''
//                     cp target/*.jar /deploy_jenkins/stage/orderflow.jar
//                     sudo systemctl restart orderflow_stage.service
//                 '''
//             }
//         }
        stage('Approval') {
            steps {
                echo 'BEFORE APPROVAL'
                input message: 'Dev verified. Deploy to staging?'
                echo 'AFTER APPROVAL'
            }
        }

        stage('Deploy Staging') {
            steps {
                echo 'STARTING STAGING DEPLOY'
                sh '''
                    echo "Running as:"
                    whoami
                    echo "Copying JAR..."
                    cp target/*.jar /deploy_jenkins/stage/orderflow.jar
                    echo "Restarting service..."
                    sudo -n systemctl restart orderflow_stage.service
                    echo "STAGING DEPLOY COMPLETE"
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
