package aia.testdriven

import org.scalatest.{WordSpecLike, MustMatchers}
import akka.testkit.{TestActorRef, TestKit}
import akka.actor._

//This test is ignored in the BookBuild, it's added to the defaultExcludedNames
//<start id="ch02-silentactor-test01"/>
class SilentActor01Test extends TestKit(ActorSystem("testsystem")) //<co id="ch02-silentactor-test01-provide-system"/>
  with WordSpecLike //<co id="ch02-silentactor-test01-wordspec"/>
  with MustMatchers //<co id="ch02-silentactor-test01-mustmatchers"/>
  with StopSystemAfterAll { //<co id="ch02-silentactor-test01-stopsystem"/>

  "A Silent Actor" must { //<co id="ch02-silentactor-test01-textmust"/>
    "change state when it receives a message, single threaded" in { //<co id="ch02-silentactor-test01-in"/>
      import SilentActorProtocol._

      val silentActor = TestActorRef[SilentActor]
      silentActor ! SilentMessage("whisper")
      silentActor.underlyingActor.state.must (contain("whisper"))
    }

    "change state when it receives a message, multi-threaded" in {
      import SilentActorProtocol._

      val silentActor = system.actorOf(Props[SilentActor], "s3")
      silentActor ! SilentMessage("whisper1")
      silentActor ! SilentMessage("whisper2")
      silentActor ! GetState(testActor)
      expectMsg(Vector("whisper1", "whisper2"))
    }
  }

}
//<end id="ch02-silentactor-test01"/>

object SilentActorProtocol {
  case class SilentMessage(data: String)
  case class GetState(receiver: ActorRef)
}

//<start id="ch02-silentactor-test01-imp"/>
class SilentActor extends Actor {
  import SilentActorProtocol._
  var internalState = Vector[String]()

  def receive = {
    case SilentMessage(data) =>
      internalState = internalState :+ data
    case GetState(receiver) => {
      println("InternalState is: " + internalState)
      receiver ! internalState
    }
  }

  def state = internalState
}
//<end id="ch02-silentactor-test01-imp"/>
