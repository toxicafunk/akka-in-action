package com.goticks

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.routing._

class RestApi(timeout: Timeout) extends HttpServiceActor
    with RestRoutes {
  implicit val requestTimeout = timeout

  def receive = runRoute(routes)

  implicit def executionContext = context.dispatcher

  def createBoxOffice = context.actorOf(BoxOffice.props, BoxOffice.name)
}

trait RestRoutes extends HttpService
    with BoxOfficeApi
    with EventMarshalling {
  import StatusCodes._

  def routes: Route = eventsRoute ~ eventRoute ~ ticketsRoute

  def eventsRoute =
    pathPrefix("events") {
      pathEndOrSingleSlash {
        get {
          println("Getting")
          // GET /events
          onSuccess(getEvents()) { events =>
            complete(OK, events)
          }
        } ~
        put {
          println("Putting")
          onSuccess(getEvents()) {
            println("Hooray!")
            events =>
              complete(OK, events)
          }
        }
      }
    }
  //<start id="ch02_event_route"/>
  def eventRoute =
    pathPrefix("events" / Segment) { event =>
      pathEndOrSingleSlash {
        post {
          // POST /events/:event
          entity(as[EventDescription]) { ed => //<co id="ch02_unmarshall_json_event"/>
            onSuccess(createEvent(event, ed.tickets)) { //<co id="ch02_call_create_event"/>
              case BoxOffice.EventCreated => complete(Created) //<co id="ch02_complete_request_with_created"/>
              case BoxOffice.EventExists =>
                val err = Error(s"$event event exists already.")
                complete(BadRequest, err) //<co id="ch02_complete_request_with_bad_request"/>
            }
          }
        } ~
        get {
          // GET /events/:event
          onSuccess(getEvent(event)) {
            _.fold(complete(NotFound))(e => complete(OK, e))
          }
        } ~
        delete {
          // DELETE /events/:event
          onSuccess(cancelEvent(event)) {
            _.fold(complete(NotFound))(e => complete(OK, e))
          }
        }
      }
    }
  //<end id="ch02_event_route"/>

  //<start id="ch02_tickets_route"/>
  def ticketsRoute =
    pathPrefix("events" / Segment / "tickets") { event =>
      post {
        pathEndOrSingleSlash {
          // POST /events/:event/tickets
          entity(as[TicketRequest]) { request => //<co id="ch02_unmarshall_ticket"/>
            onSuccess(requestTickets(event, request.tickets)) { tickets =>
              if(tickets.entries.isEmpty) complete(NotFound) //<co id="ch02_notfound_if_empty"/>
              else complete(Created, tickets) //<co id="ch02_created_with_json"/>
            }
          }
        }
      }
    }
  //<end id="ch02_tickets_route"/>
}

//<start id="ch02_boxoffice_api"/>
trait BoxOfficeApi {
  import BoxOffice._

  def createBoxOffice(): ActorRef

  implicit def executionContext: ExecutionContext
  implicit def requestTimeout: Timeout

  lazy val boxOffice = createBoxOffice()

  def createEvent(event: String, nrOfTickets: Int) =
    boxOffice.ask(CreateEvent(event, nrOfTickets))
      .mapTo[EventResponse]

  def getEvents() =
    boxOffice.ask(GetEvents).mapTo[Events]

  def getEvent(event: String) =
    boxOffice.ask(GetEvent(event))
      .mapTo[Option[Event]]

  def cancelEvent(event: String) =
    boxOffice.ask(CancelEvent(event))
      .mapTo[Option[Event]]

  def requestTickets(event: String, tickets: Int) =
    boxOffice.ask(GetTickets(event, tickets))
      .mapTo[TicketSeller.Tickets]
}
//<end id="ch02_boxoffice_api"/>
