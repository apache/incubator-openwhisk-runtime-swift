/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package runtime.actionContainers

import java.io.File

import actionContainers.ResourceHelpers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import spray.json.{JsObject, JsString}

@RunWith(classOf[JUnitRunner])
class Swift54ActionContainerTests extends SwiftActionContainerTests {
  override lazy val swiftContainerImageName = "action-swift-v5.4"
  override lazy val swiftBinaryName = "tests/dat/build/swift5.4/HelloSwift5.zip"
  lazy val partyCompile = "tests/dat/build/swift5.4/SwiftyRequest5.zip"
  lazy val partyCompileCodable = "tests/dat/build/swift5.4/SwiftyRequestCodable5.zip"

  val httpCode = """
                   | import Foundation
                   | import Dispatch
                   | #if canImport(FoundationNetworking)
                   |  import FoundationNetworking
                   | #endif
                   | func main(args:[String: Any]) -> [String:Any] {
                   |     var resp :[String:Any] = ["error":"getUrl failed"]
                   |     guard let urlStr = args["getUrl"] as? String else {
                   |         return ["error":"getUrl not found in action input"]
                   |     }
                   |     guard let url = URL(string: urlStr) else {
                   |         return ["error":"invalid url string \(urlStr)"]
                   |     }
                   |     let request = URLRequest(url: url)
                   |     let session = URLSession(configuration: .default)
                   |     let semaphore = DispatchSemaphore(value: 0)
                   |     let task = session.dataTask(with: request, completionHandler: {data, response, error -> Void in
                   |         print("done with http request")
                   |         if let error = error {
                   |             print("There was an error \(error)")
                   |         } else if let data = data,
                   |             let response = response as? HTTPURLResponse,
                   |             response.statusCode == 200 {
                   |             do {
                   |                 let respJson = try JSONSerialization.jsonObject(with: data)
                   |                 if respJson is [String:Any] {
                   |                     resp = respJson as! [String:Any]
                   |                 } else {
                   |                     resp = ["error":"Response from server is not a dictionary"]
                   |                 }
                   |             } catch {
                   |                 resp = ["error":"Error creating json from response: \(error)"]
                   |             }
                   |         }
                   |         semaphore.signal()
                   |     })
                   |     task.resume()
                   |     _ = semaphore.wait(timeout: .distantFuture)
                   |     return resp
                   | }
                 """.stripMargin

  it should "support ability to use 3rd party packages like SwiftyRequest" in {
    val zip = new File(partyCompile).toPath
    val code = ResourceHelpers.readAsBase64(zip)

    val (out, err) = withActionContainer() { c =>
      val (initCode, initRes) = c.init(initPayload(code))
      initCode should be(200)

      val args = JsObject("message" -> (JsString("serverless")))
      val (runCode, runRes) = c.run(runPayload(args))

      runCode should be(200)
      val json = runRes.get.fields.get("json")
      json shouldBe Some(args)
    }

    checkStreams(out, err, {
      case (o, e) =>
        if (enforceEmptyOutputStream) o shouldBe empty
        e shouldBe empty
    })
  }

  it should "support ability to use escaping completion in Codable" in {
    val zip = new File(partyCompileCodable).toPath
    val code = ResourceHelpers.readAsBase64(zip)

    val (out, err) = withActionContainer() { c =>
      val (initCode, initRes) = c.init(initPayload(code, main = "mainCodable"))
      initCode should be(200)

      val (runCode, runRes) = c.run(runPayload(JsObject()))

      runCode should be(200)
      runRes.get.fields.get("greeting") shouldBe Some(JsString("success"))

    }

    checkStreams(out, err, {
      case (o, e) =>
        if (enforceEmptyOutputStream) o shouldBe empty
        e shouldBe empty
    })
  }
}
