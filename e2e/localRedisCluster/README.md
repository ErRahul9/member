This `Dockerfile.redis-clusterfolder` is used to build dockerized redis cluster of 3 master & 3 replica nodes.
This image should be built & pushed to docker hub repo, from which git action `run-regression` will pull to set up bidder env
This image is pushed to docker hub repo : https://hub.docker.com/repository/docker/steelhousedev/local-redis-cluster/general

command to build & push image to docker hub repo. Make sure you are logged into docker hub
```
docker build --no-cache --progress=plain -t steelhouse/local-redis-cluster:latest -f Dockerfile.redis-cluster .
docker push steelhouse/local-redis-cluster:latest
```


Note. this folder has `redis-stable.tar.gz` file, which serves as backup for tar file required for `Dockerfile.redis-cluster`