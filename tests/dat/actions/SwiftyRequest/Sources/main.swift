// Licensed to the Apache Software Foundation (ASF) under one or more contributor
// license agreements; and to You under the Apache License, Version 2.0.

import SwiftyRequest
import Dispatch
import Foundation

func main(args: [String:Any]) -> [String:Any] {
    var resp :[String:Any] = ["error":"Action failed"]
    var echoURL:String
    if let echoUrlValue = args["url"]  {
        echoURL = echoUrlValue as! String
    } else {
        echoURL = "https://httpbin.org/post"
    }

    // setting body data to {"Data":"string"}
    let origJson: [String: Any] = args
    guard let data = try? JSONSerialization.data(withJSONObject: origJson, options: []) else {
        return ["error": "Could not encode json"]
    }
    let request = RestRequest(method: .post, url: echoURL)
    request.messageBody = data
    let semaphore = DispatchSemaphore(value: 0)
    //sending with query ?hour=9
    request.responseData(queryItems: [URLQueryItem(name: "hour", value: "9")]) { response in
        switch response.result {
        case .success(let retval):
            if let json = try? JSONSerialization.jsonObject(with: retval, options: []) as! [String:Any]  {
                resp = json
            } else {
                resp = ["error":"Response from server is not a dictionary like"]
            }
        case .failure(let error):
            resp = ["error":"Failed to get data response: \(error)"]
        }
        semaphore.signal()
    }
    _ = semaphore.wait(timeout: .distantFuture)
    return resp
}
//let r = main(args:["message":"serverless"])
//print(r)
