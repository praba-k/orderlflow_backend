pipeline {
    agent any

    parameters {
        choice(
            name: 'ENVIRONEMT',
            choices: ['dev', 'stage'],
            description: 'Choosing deployment environment'
        )
    }

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

//         stage('Intentional Failure') {
//             steps {
//                 sh 'exit 1'
//             }
//         }

        stage('Deploy') {
            steps {
                script {
                    if(params.ENVIRONEMT = 'dev') {
                        sh '''
                            cp target/*.jar /deploy_jenkins/dev/orderflow.jar
                            sudo systemctl restart orderflow_dev.service
                        '''
                    } else {
                        sh '''
                            cp target/*.jar /deploy_jenkins/stage/orderflow.jar
                            sudo systemctl restart orderflow_stage.service
                        '''
                    }

                }
            }
        }

//         stage('Deploy') {
//             steps {
//                 sh '''
//                     cp target/*.jar /deploy_jenkins/dev/orderflow.jar
//                     sudo systemctl restart orderflow_dev.service
//                 '''
//             }
//         }

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
//         stage('Approval') {
//             steps {
//                 echo 'BEFORE APPROVAL'
//                 input message: 'Dev verified. Deploy to staging?'
//                 echo 'AFTER APPROVAL'
//             }
//         }

//         stage('Deploy Staging') {
//             steps {
//                 echo 'STARTING STAGING DEPLOY'
//                 sh '''
//                     echo "Running as:"
//                     whoami
//                     echo "Copying JAR..."
//                     cp target/*.jar /deploy_jenkins/stage/orderflow.jar
//                     echo "Restarting service..."
//                     sudo -n systemctl restart orderflow_stage.service
//                     echo "STAGING DEPLOY COMPLETE"
//                 '''
//             }
//         }
    }

    post {
        success {
            archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
        }

        failure {
            echo "Pipeline failed. Check the console log..."

            emailext(
                subject: "Jenkins build failed. ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: "Build Failed. Check: ${env.BUILD_URL}",
                to: "prabakarankasinathan63@gmail.com"
            )
        }
    }
}
