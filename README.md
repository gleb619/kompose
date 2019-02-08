# kompose
Playground for continius delivery with kompose

<img src="https://raw.github.com/gleb619/kompose/master/CICD_kompose.svg?sanitize=true">

Example of project for cd to kubernetes cluster(actually rancher, but it's not so big deal).

Create individual project in your gitlab. Create branches in new project with the name of namesapces in kubernetes. For example, if you have namespaces dev, test, stage and etc. you must create branches dev, test, stage and etc.

Structure of project:
```
.
├── docker-compose.yml
└── .gitlab-ci.yml
```  

.gitlab-ci.yml contents:
```
image: <image of this repository>

stages:
  - deploy

deploy:
  stage: deploy
  artifacts:
    when: always
    expire_in: 10 minute
    paths:
      - config.yml
  only:
    - /^dev$/
  script:
    - groovy /app/main.groovy -c
```  

docker-compose.yml contents:
```
version: "3"

services:
 redis:
   image: redis:alpine
   container_name: redis
   ports: ["6378:6379"]
   labels:
     kompose.service.type: nodeport
     kompose.service.expose: "counter.example.com"
     
 db:
   image: postgres:9.4
   container_name: db
   ports: ["5432:5432"]
   volumes:
     - "./data:/var/lib/postgresql/data"
   restart: "on-failure"
   labels:
     kompose.service.type: clusterip
     kompose.service.expose: "*.test.164.org"
     kompose.backendPath: '/path_for_ingress(api gateway stuff)'
     kompose.image-pull-secret: '<name of your secret>'
     kubernetes.io/ingress.class: traefik-esbs
     traefik.ingress.kubernetes.io/rewrite-target: /
```
output:  

<img src="https://raw.github.com/gleb619/kompose/master/build_process.png">
