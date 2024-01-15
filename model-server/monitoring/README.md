# How to use monitoring

1. Build `model-server` with changes on this branch
2. Run `docker-build-model.sh` in root folder to create a latest docker container with the monitoring endpoint
3. Optional: Adjust lines 8-10 in `model-server/monitoring/docker-compose.yaml` to load a dump of data into the `model-server` on startup
3. Run `docker-compose up` in `model-server/monitoring/`
4. Navigate to the dashboard at http://localhost:3001/
5. Generate some traffic on the `model-server`, e.g. via the swagger interface at http://127.0.0.1:28101/swagger
