
# customs-data-store

This repository contains the code for a persistent cache holding customs related data.
It uses graphql queries for querying, updating and inserting.

### GraphQL examples

Example queries for retrieveing data (Select):

Will return the values held in 'address' only for the given EORI.
```json
{ "query": "query { byEori( eori: \"GB12345678\") { notificationEmail { address }  } }"}
```

Will return the values held in 'address' and 'timestamp' for a given EORI.
```json
{ "query": "query { byEori( eori: \"GB12345678\") { notificationEmail { address, timestamp } } }"}
```

Example queries for upserting data (Insert/Update)

Updating/Inserting the dates on an EORI or inserting it; without an email:
```json
{"query" : "mutation {byEori(eoriHistory:{eori:\"GB12345678\" validFrom:\"20180101\" validUntil:\"20200101\"} )}" }
```

Updating/Inserting an EORI with an email and timestamp:
```json
{"query" : "mutation {byEori(eoriHistory:{eori:\"EORI11223344\" validFrom:\"20180101\" validUntil:\"20200101\"}, notificationEmail: {address: \"rashmidth@rich-contractors.com\", timestamp: \"timestamp\"} )}" }
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
