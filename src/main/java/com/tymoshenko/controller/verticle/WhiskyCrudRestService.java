package com.tymoshenko.controller.verticle;

import com.tymoshenko.controller.repository.CrudService;
import com.tymoshenko.model.Whisky;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Creates a Router object which routes HTTP requests to first matching URL.
 * REST methods for CRUD API are mapped to URLs inside the only public method of this class: createHttpRequestRouter(Vertx vertx).
 *
 * @author Yakiv Tymoshenko
 * @since 15.03.2016
 */
@Service
public class WhiskyCrudRestService {

    // REST endpoint URLs
    public static final String REST_WHISKYS_URL = "/rest/whiskys";
    public static final String REST_WHISKYS_URL_WITH_ID = REST_WHISKYS_URL + "/:id";

    // HTTP req/res constants
    public static final String CONTENT_TYPE = "content-type";
    public static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json; charset=utf-8";
    public static final String TEXT_HTML = "text/html";

    // HTTP status codes
    public static final int STATUS_CODE_OK = 200;
    public static final int STATUS_CODE_OK_CREATED = 201;
    public static final int STATUS_CODE_OK_NO_CONTENT = 204;
    public static final int STATUS_CODE_BAD_REQUEST = 400;
    public static final int STATUS_CODE_NOT_FOUND = 404;

    private static final Logger LOG = LoggerFactory.getLogger(WhiskyCrudRestService.class);

    @Autowired
    private CrudService<Whisky> whiskyCrudService;

    /**
     * Creates a Router which routs REST (HTTP) requests to the first matching URL.
     *
     * @param vertx HttpServer Vertex
     * @return a REST request router.
     * The router receives request from an HttpServer and routes it to the first matching Route that it contains.
     * A router can contain many routes.
     * Routers are also used for routing failures.
     */
    public Router createHttpRequestRouter(Vertx vertx) {
        Router router = Router.router(vertx);

        // Bind "/" to our hello message.
        router.route("/").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            final String _helloMessage = "<h1>Hello from my first Vert.x 3 application</h1>";
            response
                    .putHeader(CONTENT_TYPE, TEXT_HTML)
                    .end(_helloMessage);
        });

        // Register request body handler. It will convert a request body into format such as String or JSON.
        router.route(REST_WHISKYS_URL + "*").handler(BodyHandler.create());

        // Register REST methods for CRUD operations
        // Create
        router.post(REST_WHISKYS_URL).handler(this::addOne);
        // Read one
        router.get(REST_WHISKYS_URL_WITH_ID).handler(this::getOne);
        // Read all
        router.get(REST_WHISKYS_URL).handler(this::getAll);
        // Update
        router.put(REST_WHISKYS_URL_WITH_ID).handler(this::updateOne);
        // Delete
        router.delete(REST_WHISKYS_URL_WITH_ID).handler(this::deleteOne);

        return router;
    }

    /**
     * Create an Whisky entity.
     *
     * @param routingContext Represents the context for the handling of a request in Vert.x-Web.
     */
    private void addOne(RoutingContext routingContext) {
        final Whisky whisky;
        try {
            whisky = Json.decodeValue(routingContext.getBodyAsString(), Whisky.class);

            whiskyCrudService.save(whisky);

            routingContext.response()
                    .setStatusCode(STATUS_CODE_OK_CREATED)
                    .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
                    .end(Json.encodePrettily(whisky));
        } catch (DecodeException e) {
            LOG.error(e.getLocalizedMessage());
            routingContext.response()
                    .setChunked(true)
                    .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
                    .write("Malformed Whisky object")
                    .setStatusCode(STATUS_CODE_BAD_REQUEST)
                    .end();
        }
    }

    /**
     * Get a Whisky by ID.
     * Should provide an ID in the request URL.
     *
     * @param routingContext Represents the context for the handling of a request in Vert.x-Web.
     */
    private void getOne(RoutingContext routingContext) {
        Long id = getWhiskyId(routingContext);
        if (id != null) {
            Whisky whisky = whiskyCrudService.readOne(id);
            if (whisky == null) {
                routingContext.response().setStatusCode(STATUS_CODE_NOT_FOUND).end("Whisky not found for id=" + id);
            } else {
                routingContext.response()
                        .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
                        .end(Json.encodePrettily(whisky));
            }
        }
    }

    /**
     * Get all Whisky instances from the DB.
     *
     * @param routingContext Represents the context for the handling of a request in Vert.x-Web.
     */
    private void getAll(RoutingContext routingContext) {
        List<Whisky> whiskyList = whiskyCrudService.readAll();
        routingContext.response()
                .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
                .end(Json.encodePrettily(whiskyList));
    }

    /**
     * Update a Whisky instance.
     * Should provide an ID in the request URL and
     * new values for Whisky.name and Whisky.origin in the request body (json).
     *
     * @param routingContext Represents the context for the handling of a request in Vert.x-Web.
     */
    private void updateOne(RoutingContext routingContext) {
        Long id = getWhiskyId(routingContext);
        if (id == null) {
            return;
        }
        try {
            JsonObject json = routingContext.getBodyAsJson();
            if (json == null) {
                routingContext.response().setStatusCode(STATUS_CODE_BAD_REQUEST).end("Malformed Whisky object.");
            } else {
                Whisky whisky = whiskyCrudService.readOne(id);
                if (whisky == null) {
                    routingContext.response().setStatusCode(STATUS_CODE_NOT_FOUND).end();
                } else {
                    whisky.setName(json.getString("name"));
                    whisky.setOrigin(json.getString("origin"));

                    whisky = whiskyCrudService.save(whisky);

                    routingContext.response()
                            .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
                            .end(Json.encodePrettily(whisky));
                }
            }
        } catch (DecodeException e) {
            routingContext.response().setStatusCode(STATUS_CODE_BAD_REQUEST).end("Malformed Whisky object.");
        }
    }

    /**
     * Deletes a Whisky instance by ID.
     * Should provide an ID in the request URL.
     *
     * @param routingContext Represents the context for the handling of a request in Vert.x-Web.
     */
    private void deleteOne(RoutingContext routingContext) {
        Long id = getWhiskyId(routingContext);
        if (id != null) {
            try {
                whiskyCrudService.delete(id);
            } catch (EmptyResultDataAccessException e) {
                // Trying to delete an entity which not exists
                routingContext.response().setStatusCode(STATUS_CODE_NOT_FOUND).end("Can not delete Whisky because it does not exist. ID=" + id);
                return;
            }
            routingContext.response().setStatusCode(STATUS_CODE_OK_NO_CONTENT).end("Deleted.");
        }
    }

    private Long getWhiskyId(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        Long idAsLong = null;
        try {
            idAsLong = Long.valueOf(id);
        } catch (NumberFormatException e) {
            LOG.error(e.getLocalizedMessage());

            routingContext.response()
                    .setChunked(true)
                    .putHeader(CONTENT_TYPE, TEXT_HTML)
                    .write(String.format("Bad ID. ID=\"%s\"", id))
                    .setStatusCode(STATUS_CODE_BAD_REQUEST)
                    .end();
        }
        return idAsLong;
    }
}