/* GStreamer
 * Copyright (C) 2000,2001,2002,2003,2005
 *           Thomas Vander Stichele <thomas at apestaart dot org>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 */
 
/* compile:
 gcc sounddetect.c -o sounddetect -lm `pkg-config --cflags --libs gstreamer-1.0`
*/

#include <string.h>
#include <math.h>

#define GLIB_DISABLE_DEPRECATION_WARNINGS

#include <gst/gst.h>


static gboolean
message_handler (GstBus * bus, GstMessage * message, gpointer data)
{

  if (message->type == GST_MESSAGE_ELEMENT) {
    const GstStructure *s = gst_message_get_structure (message);
    const gchar *name = gst_structure_get_name (s);

    if (strcmp (name, "level") == 0) {
      gint channels;
      GstClockTime endtime;
      gdouble rms_dB, peak_dB, decay_dB;
      gdouble rms;
      const GValue *array_val;
      const GValue *value;
      GValueArray *rms_arr, *peak_arr, *decay_arr;
      gint i;

      // if (!gst_structure_get_clock_time (s, "endtime", &endtime))
        // g_warning ("Could not parse endtime");

      /* the values are packed into GValueArrays with the value per channel */
      // array_val = gst_structure_get_value (s, "rms");
      // rms_arr = (GValueArray *) g_value_get_boxed (array_val);

      array_val = gst_structure_get_value (s, "peak");
      peak_arr = (GValueArray *) g_value_get_boxed (array_val);

      // array_val = gst_structure_get_value (s, "decay");
      // decay_arr = (GValueArray *) g_value_get_boxed (array_val);

      /* we can get the number of channels as the length of any of the value
       * arrays */
      // channels = rms_arr->n_values;
      // g_print ("endtime: %" GST_TIME_FORMAT ", channels: %d\n",
          // GST_TIME_ARGS (endtime), channels);

        // g_print ("channel %d\n", i);
        // value = g_value_array_get_nth (rms_arr, i);
        // rms_dB = g_value_get_double (value);

        value = g_value_array_get_nth (peak_arr, 0);
        peak_dB = g_value_get_double (value);
        // g_print("peak: %f dB\n", peak_dB);
        g_print("%f\n", peak_dB);

        // value = g_value_array_get_nth (decay_arr, i);
        // decay_dB = g_value_get_double (value);
        // g_print ("    RMS: %f dB, peak: %f dB, decay: %f dB\n",
            // rms_dB, peak_dB, decay_dB);

        /* converting from dB to normal gives us a value between 0.0 and 1.0 */
        // rms = pow (10, rms_dB / 20);
        // g_print ("    normalized rms value: %f\n", rms);
    }
  }

  return TRUE;
}

int
main (int argc, char *argv[])
{

	
  GstElement *audiosrc, *audioconvert, *level, *fakesink;
  GstElement *pipeline;
  GstCaps *caps;
  GstBus *bus;
  guint watch_id;
  GMainLoop *loop;

  gst_init (&argc, &argv);


	GError *error = NULL;

	gchar *audio_device = argv[1];
	gchar *pl = g_strconcat (
		"alsasrc device=hw:", audio_device, " ! audioconvert ! level post-messages=true interval=500000000 ! fakesink sync=true",
		 NULL);
	pipeline =	gst_parse_launch (pl, &error);
	g_free(pl);
	
	if (error) {
		g_printerr ("Failed to parse launch: %s\n", error->message);
		return -1;
	}


  bus = gst_element_get_bus (pipeline);
  watch_id = gst_bus_add_watch (bus, message_handler, NULL);

  gst_element_set_state (pipeline, GST_STATE_PLAYING);

  /* we need to run a GLib main loop to get the messages */
  loop = g_main_loop_new (NULL, FALSE);
  g_main_loop_run (loop);

  g_source_remove (watch_id);
  g_main_loop_unref (loop);
  return 0;
}


