package com.faendir.acra.security

import com.faendir.acra.navigation.RouteParams
import com.vaadin.flow.server.auth.AccessAnnotationChecker
import org.springframework.stereotype.Component
import java.lang.reflect.Method
import java.security.Principal
import java.util.function.Function

@Component
class AcrariumAccessAnnotationChecker(private val routeParams: RouteParams) : AccessAnnotationChecker() {
    override fun hasAccess(method: Method, principal: Principal?, roleChecker: Function<String, Boolean>): Boolean {
        return SecurityUtils.hasAccess(routeParams::appId, method.declaringClass) || super.hasAccess(method, principal, roleChecker)
    }

    override fun hasAccess(cls: Class<*>, principal: Principal?, roleChecker: Function<String, Boolean>): Boolean {
        return SecurityUtils.hasAccess(routeParams::appId, cls) || super.hasAccess(cls, principal, roleChecker)
    }
}