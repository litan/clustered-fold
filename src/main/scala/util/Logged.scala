package util

import org.slf4j.{Logger, LoggerFactory}


trait Logged {
  var _log: Logger = null
  var _namedLog: Logger = null
  def log = {
    if (_log == null) {
      _log = LoggerFactory.getLogger(this.getClass)
    }
    _log
  }

  def log(name: String) = {
    if (_namedLog == null) {
      _namedLog = LoggerFactory.getLogger(name)
    }
    _namedLog
  }
}
