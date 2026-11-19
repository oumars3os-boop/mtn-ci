package com.example.mtnsimpro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SecurityViewModel(application: Application) : AndroidViewModel(application) {
    private val securityManager = SecurityManager(application)
    
    // Initialiser avec le statut local (une fois à l'installation)
    private val _isActivated = MutableStateFlow(securityManager.isActivatedLocally())
    val isActivated: StateFlow<Boolean> = _isActivated.asStateFlow()

    private val _isRemoteBlocked = MutableStateFlow(false)
    val isRemoteBlocked: StateFlow<Boolean> = _isRemoteBlocked.asStateFlow()

    private val _deviceId = MutableStateFlow(securityManager.getDeviceId())
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    init {
        // Lancer la vérification initiale ET la boucle de temps réel
        startRealTimeMonitoring()
    }

    private fun startRealTimeMonitoring() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    // --- NOUVELLES VÉRIFICATIONS DE SÉCURITÉ ---
                    val context = getApplication<Application>()
                    val isCompromised = SecurityUtils.isDeviceRooted() || 
                                      SecurityUtils.isRunningOnEmulator() || 
                                      SecurityUtils.isTampered(context) ||
                                      !SecurityUtils.checkAppSignature(context)

                    if (isCompromised) {
                        _isRemoteBlocked.value = true
                        // Optionnel: On peut aussi forcer la fermeture ou un message spécifique
                    }

                    val (isBlocked, isAutoActivated) = securityManager.checkRemoteStatus()
                    _isRemoteBlocked.value = isBlocked || isCompromised
                    if (isAutoActivated && !isCompromised) {
                        _isActivated.value = true
                    } else if (isBlocked || isCompromised) {
                        _isActivated.value = false
                    }
                } catch (_: Exception) {}
                
                // Vérifier toutes les 30 secondes pour le temps réel
                delay(30000) 
            }
        }
    }

    fun checkRemoteBlock() {
        // Forcer une vérification manuelle immédiate
        viewModelScope.launch(Dispatchers.IO) {
            val (isBlocked, isAutoActivated) = securityManager.checkRemoteStatus()
            _isRemoteBlocked.value = isBlocked
            if (isAutoActivated) {
                _isActivated.value = true
            } else if (isBlocked) {
                _isActivated.value = false
            }
        }
    }

    fun getCalculatedKey(deviceId: String? = null): String {
        val targetId = deviceId ?: securityManager.getDeviceId()
        return securityManager.calculateSecurityKey(targetId)
    }

    fun attemptActivation(key: String): Boolean {
        val success = securityManager.activate(key)
        if (success) {
            _isActivated.value = true
        }
        return success
    }

    fun resetActivation() {
        securityManager.resetLocalActivation()
        _isActivated.value = false
    }

    fun verifyAdminPassword(password: String): Boolean {
        return securityManager.checkAdminPassword(password)
    }
}
