package com.smsmurilo.blehealth

import com.smsmurilo.blehealth.domain.VitalsAlert
import com.smsmurilo.blehealth.domain.VitalsAlertPolicy
import com.smsmurilo.blehealth.ui.MonitorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VitalsTest {

    @Test
    fun `alerts only when abnormal rate is sustained, once per episode`() {
        val policy = VitalsAlertPolicy(sustainMs = 10_000)
        assertNull(policy.onHeartRate(140, true, 0))
        assertNull(policy.onHeartRate(140, true, 5_000))
        assertEquals(VitalsAlert.Tachycardia(141), policy.onHeartRate(141, true, 10_000))
        assertNull(policy.onHeartRate(142, true, 12_000))
        assertNull(policy.onHeartRate(80, true, 13_000))
        assertNull(policy.onHeartRate(40, true, 14_000))
        assertEquals(VitalsAlert.Bradycardia(40), policy.onHeartRate(40, true, 24_000))
    }

    @Test
    fun `single spike does not alert`() {
        val policy = VitalsAlertPolicy()
        assertNull(policy.onHeartRate(180, true, 0))
        assertNull(policy.onHeartRate(75, true, 1_000))
        assertNull(policy.onHeartRate(180, true, 9_000))
    }

    @Test
    fun `lost contact is reported immediately`() {
        assertEquals(VitalsAlert.SensorContactLost, VitalsAlertPolicy().onHeartRate(0, false, 0))
    }

    @Test
    fun `rmssd hrv metric`() {
        assertNull(MonitorViewModel.rmssd(listOf(800, 810)))
        assertEquals(20, MonitorViewModel.rmssd(listOf(800, 820, 800, 820)))
    }
}
