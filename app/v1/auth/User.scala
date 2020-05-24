package v1.auth

import com.mohiva.play.silhouette.api.Identity

case class User(id: String, email: String, isAdmin: Boolean = false) extends Identity
