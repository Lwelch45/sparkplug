package springnz.sparkplug

import akka.actor._
import akka.testkit.{ ImplicitSender, TestKit }
import com.typesafe.config.ConfigFactory
import org.scalatest._
import springnz.sparkplug.core.{ Configurer, LocalConfigurer }
import springnz.sparkplug.executor.MessageTypes.{ ClientReady, JobRequest, JobSuccess, ServerReady }
import springnz.sparkplug.executor.{ Constants, ExecutorService }

import scala.concurrent.duration._

class ExecutorServiceTests(_system: ActorSystem)
    extends TestKit(_system) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  case object ServerTerminated

  def this() = this(ActorSystem("TestSystem", ConfigFactory.load().getConfig(Constants.defaultConfigSectionName)))

  "notify the client that server is ready" in new Fixture(self, "client1", "testBroker1") {
    expectMsg(1 seconds, ServerReady)
  }

  "successfuly execute a job request via a plugin" in new Fixture(self, "client2", "testBroker2") {
    expectMsg(1 seconds, ServerReady)
    val requestBroker = system.actorSelection(s"/user/testBroker2")
    val request = JobRequest("springnz.sparkplug.examples.LetterCountPlugin", None)
    requestBroker ! request
    expectMsg[JobSuccess](6 seconds, JobSuccess(request, (2, 2)))
  }

  "deathwatch on client (base case)" in new Fixture(self, "client3", "testBroker3") {
    expectMsg(1 seconds, ServerReady)
    val requestBroker = system.actorSelection(s"/user/testBroker3")
    // give it something to do for a while
    val request = JobRequest("springnz.sparkplug.examples.WaitPlugin", None)
    requestBroker ! request
    expectMsgType[JobSuccess](3 second)
  }

  "deathwatch on client (with poison pill should terminate server)" in new Fixture(self, "client4", "testBroker4") {
    expectMsg(1 seconds, ServerReady)
    val requestBroker = system.actorSelection(s"/user/testBroker4")
    // give it something to do for a while
    val request = JobRequest("springnz.sparkplug.examples.WaitPlugin", None)
    requestBroker ! request
    clientActor ! PoisonPill
    expectNoMsg(3 second)
  }

  class Fixture(probe: ActorRef, clientName: String, brokerName: String) {

    val executorService = new ExecutorService("TestService", brokerName) {
      // Run it locally in Spark
      override val configurer: Configurer = new LocalConfigurer("TestService", None)
    }

    executorService.start(system, s"/user/$clientName")

    val clientActor = system.actorOf(Props(new Actor {
      override def receive = {
        case ServerReady ⇒
          probe forward ServerReady
          sender ! ClientReady
      }
    }), clientName)
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
