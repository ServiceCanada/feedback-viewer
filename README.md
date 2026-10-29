# Feedback Viewer

This is a web application for viewing and managing feedback data submitted by users. It is built with Spring Boot Java on the backend, and uses MongoDB to store the data. The frontend is implemented with HTML, CSS, and JavaScript.

## Features

The Feedback Viewer provides the following features:

- **View feedback**: Users can browse the list of feedback entries, which includes information such as a comment, Page URL, institution, and more.
- **Filter feedback**: Users can filter the feedback entries based on various criteria, such as date range, section, theme, and institution.
- **Export feedback**: Users can export the feedback entries to a CSV or Excel file for further analysis.

## Running locally with Docker

The quickest way to get started. You only need Docker; no Java, Maven, MongoDB or secrets.

1. Clone the repository and run `docker-compose up -d` (or `docker compose up -d`) from its root.
2. Open `http://localhost:8080/signup` and create an account. The first account on a fresh database
   is an enabled admin, so you can log in straight away.

The first start downloads Maven dependencies and can take a minute or two. The app runs from source
against a local MongoDB, so nothing you do touches production data.

### Making changes

- **HTML templates, CSS and JavaScript:** save the file and refresh the browser.
- **Java:** save the file. The app recompiles and restarts within about 10 seconds; refresh after
  that. If the code doesn't compile, the error appears in the logs and the app keeps running the last
  working version.

### Looking at logs

```sh
docker-compose logs -f app            # follow the app's logs live (Ctrl+C to stop watching)
docker-compose logs --since 5m app    # just the last 5 minutes
docker-compose logs -f                # the app and MongoDB together
```

When you save a Java file you'll see `Java change detected, recompiling...`, then
`Preloading caches before web server starts...` once the app restarts. Compile errors show up as
`[ERROR]` lines between the two. If you started with `docker-compose up` (without `-d`), the logs
are already streaming in that terminal.

### Looking at the database

The local database starts empty, so the feedback pages show no rows until you add some. MongoDB is
on `localhost:27017` (database `pagesuccess`); connect with MongoDB Compass, or open a shell with:

```sh
docker-compose exec mongodb mongosh pagesuccess
```

Feedback lives in the `problem` collection. A row needs `processed: "true"` to appear in the
table, for example:

```js
db.problem.insertOne({ url: "https://www.canada.ca/en/test.html", title: "Test page",
  problemDetails: "Sample comment", institution: "ESDC", language: "en",
  problemDate: "2026-10-29", timeStamp: "2026-10-29T12:00:00Z", processed: "true" })
```

The app counts rows when it starts, so run `docker-compose restart app` after adding data.

### Everyday commands

| Command | What it does |
| --- | --- |
| `docker-compose up -d` | Start everything in the background |
| `docker-compose ps` | Check what's running |
| `docker-compose restart app` | Restart the app (e.g. after adding data) |
| `docker-compose stop` | Stop everything; your data is kept |
| `docker-compose down -v` | Stop and wipe the local database and Maven cache |

### Troubleshooting

- **`docker: 'compose' is not a docker command`:** use `docker-compose` (with a hyphen) instead.
- **Port 8080 or 27017 is already in use:** stop whatever else is using it, such as a copy of the
  app started from your IDE or a local MongoDB.
- **A Java change doesn't show up:** check `docker-compose logs --since 5m app` for a compile error.
- **Debugging:** a Java debugger can attach to `localhost:8000`.

## Installation

To install and run the Feedback Viewer without Docker, follow these steps:

1. Clone the repository: `git clone https://github.com/alpha-canada-ca/feedback-viewer.git`
2. Install the required dependencies: `./mvnw install`
3. Start the application: `./mvnw spring-boot:run`

The application should now be accessible at `http://localhost:8080`.

## Configuration

The Feedback Viewer can be configured by modifying the following properties in the `application.properties` file:

- `spring.data.mongodb.uri`: The connection string for the MongoDB database.

## Contributing

Contributions to the Feedback Viewer are welcome! To contribute, follow these steps:

1. Fork the repository.
2. Create a new branch for your changes: `git checkout -b my-feature-branch`.
3. Make your changes and commit them: `git commit -m "Add new feature"`.
4. Push your changes to your fork: `git push origin my-feature-branch`.
5. Submit a pull request to the main repository.
