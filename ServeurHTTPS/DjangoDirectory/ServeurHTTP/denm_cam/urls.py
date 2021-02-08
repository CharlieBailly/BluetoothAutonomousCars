from django.urls import path
from rest_framework.urlpatterns import format_suffix_patterns
from . import views

urlpatterns = [
    path('messages/denm/', views.DenmList.as_view()),
    path('messages/denm/<int:pk>/', views.DenmDetail.as_view()),

    path('messages/cam/', views.CamList.as_view()),
    path('messages/cam/<int:pk>/', views.CamDetail.as_view()),


]

urlpatterns = format_suffix_patterns(urlpatterns)
