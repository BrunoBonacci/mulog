# Attribute name mangling on Elasticsearch

Elasticsearch can ingest documents without the necessity to define a
schema ahead of time.

It is generally a good idea to define a [Mapping](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html)
(schema in Elasticsearch terms) for your indices.

A Mapping allows for better optimisations of your indexes.
Elasticsearch is capable of inferring the Mapping if one is not
provided explicitly.

This capability makes very easy and quick to get started with Elasticsearch.
However, if the inferred type is incorrect it can lead to problems.

For example, if you have a record for your Order as following:

```
{
  "orderId": 43252,
  "userId": "lksjdhskldfjhg",
  "product": "sku-123",
  "quantity": 4,
  "unitPrice": 25.0,
  "totalPrice": 100.0
}
```

The `orderId` is a number automatically incremented by the DB.
If you index this document in Elasticsearch with:

```
>>> PUT http://localhost:9200/orders/_doc/43253
>>> Content-Type: application/json
>>>
>>> {
>>>   "orderId": 43253,
>>>   "userId": "lksjdhskldfjhg",
>>>   "product": "sku-123",
>>>   "quantity": 4,
>>>   "unitPrice": 25.0,
>>>   "totalPrice": 100.0
>>> }
```

you'll get a response like:

```
<<< HTTP/1.1 201 Created
<<< Location: /orders/_doc/43253
<<< content-type: application/json; charset=UTF-8
<<< content-length: 159
<<<
<<< {
<<<   "_index": "orders",
<<<   "_type": "_doc",
<<<   "_id": "43253",
<<<   "_version": 1,
<<<   "result": "created",
<<<   "_shards": {
<<<     "total": 2,
<<<     "successful": 1,
<<<     "failed": 0
<<<   },
<<<   "_seq_no": 15,
<<<   "_primary_term": 1
<<< }
```

Everything looks fine so far. You can query the order with:

```
>>> GET http://localhost:9200/orders/_doc/43253
>>> Content-Type: application/json
>>>

<<< HTTP/1.1 200 OK
<<< content-type: application/json; charset=UTF-8
<<< content-length: 254
<<<
<<< {
<<<   "_index": "orders",
<<<   "_type": "_doc",
<<<   "_id": "43253",
<<<   "_version": 1,
<<<   "_seq_no": 15,
<<<   "_primary_term": 1,
<<<   "found": true,
<<<   "_source": {
<<<     "orderId": 43253,
<<<     "userId": "lksjdhskldfjhg",
<<<     "product": "sku-123",
<<<     "quantity": 4,
<<<     "unitPrice": 25.0,
<<<     "totalPrice": 100.0
<<<   }
<<< }
```

However if you decide to change the order number to be a UUID
(String), at the first attempt to index a new object in the same index
you will get the following error:


```
>>> PUT http://localhost:9200/orders/_doc/44171829-b1e4-4272-8d08-c2d4e8db5d64
>>> Content-Type: application/json
>>>
>>> {
>>>   "orderId": "44171829-b1e4-4272-8d08-c2d4e8db5d64",
>>>   "userId": "lksjdhskldfjhg",
>>>   "product": "sku-123",
>>>   "quantity": 1,
>>>   "unitPrice": 25.0,
>>>   "totalPrice": 25.0
>>> }

<<< HTTP/1.1 400 Bad Request
<<< content-type: application/json; charset=UTF-8
<<< content-length: 595
<<<
<<< {
<<<   "error": {
<<<     "root_cause": [
<<<       {
<<<         "type": "mapper_parsing_exception",
<<<         "reason": "failed to parse field [orderId] of type [long] in document with id '44171829-b1e4-4272-8d08-c2d4e8db5d64'. Preview of field's value: '44171829-b1e4-4272-8d08-c2d4e8db5d64'"
<<<       }
<<<     ],
<<<     "type": "mapper_parsing_exception",
<<<     "reason": "failed to parse field [orderId] of type [long] in document with id '44171829-b1e4-4272-8d08-c2d4e8db5d64'. Preview of field's value: '44171829-b1e4-4272-8d08-c2d4e8db5d64'",
<<<     "caused_by": {
<<<       "type": "illegal_argument_exception",
<<<       "reason": "For input string: \"44171829-b1e4-4272-8d08-c2d4e8db5d64\""
<<<     }
<<<   },
<<<   "status": 400
<<< }
```

The order will be rejected and not indexed. In the older versions of
ELS it used to also coerce types automatically which it was causing
even more problems.  For example, in the above order the field
`quantity` is a integer number.  What if you want to create a order
for `7.5` units of something (like Kg, Meters).  Older versions of ELS
would coerce the `7.5` to a integer (`7`) truncating the decimal part.

In ***μ/log*** you might collect the events from many different application
into a single index. How can you ensure that all the applications have the
type and the same meaning for all the fields? Are fields like `id`, `quantity`,
`discount` all of the same type in all the applications? Can you guarantee that?

Because I know that this is a very hard problem to deal with, and even
if now you can guarantee that every field with a given name across all
the application have the same type can you guarantee that it will
never change?

The solution that ***μ/log*** is adopting is to *mangle* the field
names so that they won't conflict in the index.

For example, our order map will be changed into

```
{
  "orderId.i": 43252,
  "userId.s": "lksjdhskldfjhg",
  "product.s": "sku-123",
  "quantity.i": 4,
  "unitPrice.f": 25.0,
  "totalPrice.f": 100.0
}
```

Notice that the `orderId` is now `orderId.i` (`i` as *Integer*). So
that if in the future you want to change the type or another
application is using the same field name but with a different
type/meaning then the two won't clash. This is how the new order could
look like:

```
{
  "orderId.s": "44171829-b1e4-4272-8d08-c2d4e8db5d64",
  "userId.s": "lksjdhskldfjhg",
  "product.s": "sku-123",
  "quantity.i": 1,
  "unitPrice.f": 25.0,
  "totalPrice.f": 25.0
}
```

Indexed as `orderId.s` won't clash with `orderId.i`.

The name mangling is enabled by default, but it can be disabled via
configuration:

``` clojure
{:type :elasticsearch

 ;; Elasticsearch endpoint (REQUIRED)
 :url  "http://localhost:9200/"

 ;; Whether or not to change the attribute names
 ;; to facilitate queries and avoid type clashing
 :name-mangling false
 }
```

Other changes are made to remove reserved characters from the name and
make it easier to run queries from Kibana.

Here is the list of changes (these changes are applied *ONLY* to the
*field names*, not the values):

  - Append the type letter indicator to each field (`count` -> `count.i`)
  - Removes semicolons `:` at the beginning of the words, like for
    keywords (`:blue` -> `blue`)
  - Turns slashes `/` like in namespaced keywords into dots `.`
    (`:mulog/duration` -> `mulog.duration` )
  - Turns any character which is not a letter, a digit or a dot `.`
    into a underscore `_` ( `event-name` -> `event_name`)
