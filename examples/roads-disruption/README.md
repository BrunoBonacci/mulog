# roads-disruptions

This is a sample application to show the use of ***μ/log*** to log events
and instrument an application.

The application poll the [Transport For London (TFL)
API](https://api-portal.tfl.gov.uk/docs) for road disruptions.  It
tracks in realtime the disruptions to the main road corridors around
London (UK).  A REST api allow the user to list all active disruptions
around London.

***This project is intended for demonstration purpose only!***

## Testing

1. Start Kafka and ElasticSearch in background
``` shell
docker-compose rm -f && docker-compose up
```
2. Wait until all services are properly started and logs are settled.
3. Run the disruption service
``` shell
lein do clean, run
```
4. Open: http://localhost:9000/ for Kibana, click on **Management ->
   Index Patterns -> Create Index pattern**, then add the index
   pattern `mulog-*`, click **Next**, Choose `@timestamp` as time
   field, then click on **Create index pattern**.
5. Finally, Click on **Discover** and see the events as they arrive.
6. The same events should be printed in the console and in Kafka topic called **mulog**.
