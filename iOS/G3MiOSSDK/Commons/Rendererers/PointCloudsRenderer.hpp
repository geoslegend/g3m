//
//  PointCloudsRenderer.hpp
//  G3MiOSSDK
//
//  Created by Diego Gomez Deck on 8/19/14.
//
//

#ifndef __G3MiOSSDK__PointCloudsRenderer__
#define __G3MiOSSDK__PointCloudsRenderer__

#include "DefaultRenderer.hpp"

#include "IThreadUtils.hpp"
#include "IBufferDownloadListener.hpp"
#include "TimeInterval.hpp"

class IDownloader;
class Sector;


class PointCloudsRenderer : public DefaultRenderer {
public:

  class PointCloudMetadataListener {
  public:
    virtual ~PointCloudMetadataListener() {
    }

    virtual void onMetadata(long long pointsCount,
                            const Sector& sector,
                            double minHeight,
                            double maxHeight) = 0;
  };


private:

  class PointCloud;

  class PointCloudMetadataParserAsyncTask : public GAsyncTask {
  private:
    PointCloud* _pointCloud;
    IByteBuffer* _buffer;
    long long _pointsCount;
    Sector* _sector;
    double _minHeight;
    double _maxHeight;

  public:
    PointCloudMetadataParserAsyncTask(PointCloud* pointCloud,
                                      IByteBuffer* buffer) :
    _pointCloud(pointCloud),
    _buffer(buffer),
    _pointsCount(-1),
    _sector(NULL),
    _minHeight(0),
    _maxHeight(0)
    {
    }

    ~PointCloudMetadataParserAsyncTask();

    void runInBackground(const G3MContext* context);

    void onPostExecute(const G3MContext* context);

  };


  class PointCloudMetadataDownloadListener : public IBufferDownloadListener {
  private:
    PointCloud*  _pointCloud;

  public:
    PointCloudMetadataDownloadListener(PointCloud* pointCloud) :
    _pointCloud(pointCloud)
    {
    }

    void onDownload(const URL& url,
                    IByteBuffer* buffer,
                    bool expired);

    void onError(const URL& url);

    void onCancel(const URL& url);

    void onCanceledDownload(const URL& url,
                            IByteBuffer* buffer,
                            bool expired);

  };

  class PointCloud {
  private:
#ifdef C_CODE
    const URL         _serverURL;
#endif
#ifdef JAVA_CODE
    private final URL _serverURL;
#endif
    const std::string _cloudName;

    const long long    _downloadPriority;
#ifdef C_CODE
    const TimeInterval _timeToCache;
#endif
#ifdef JAVA_CODE
    private final TimeInterval _timeToCache;
#endif
    const bool         _readExpired;

    PointCloudMetadataListener* _metadataListener;
    bool _deleteListener;

    IDownloader* _downloader;
    const IThreadUtils* _threadUtils;

    bool _downloadingMetadata;
    bool _errorDownloadingMetadata;
    bool _errorParsingMetadata;

    long long _pointsCount;
    Sector* _sector;
    double _minHeight;
    double _maxHeight;


  public:
    PointCloud(const URL& serverURL,
               const std::string& cloudName,
               long long downloadPriority,
               const TimeInterval& timeToCache,
               bool readExpired,
               PointCloudMetadataListener* metadataListener,
               bool deleteListener) :
    _serverURL(serverURL),
    _cloudName(cloudName),
    _downloadPriority(downloadPriority),
    _timeToCache(timeToCache),
    _readExpired(readExpired),
    _metadataListener(metadataListener),
    _deleteListener(deleteListener),
    _downloader(NULL),
    _threadUtils(NULL),
    _downloadingMetadata(false),
    _errorDownloadingMetadata(false),
    _errorParsingMetadata(false),
    _pointsCount(-1),
    _sector(NULL),
    _minHeight(0),
    _maxHeight(0)
    {
    }

    ~PointCloud();

    void initialize(const G3MContext* context);

    RenderState getRenderState(const G3MRenderContext* rc);

    void errorDownloadingMetadata();

    void downloadedMetadata(IByteBuffer* buffer);

    void parsedMetadata(long long pointsCount,
                        Sector* sector,
                        double minHeight,
                        double maxHeight);

    void render(const G3MRenderContext* rc,
                GLState* glState);

  };



  std::vector<PointCloud*> _clouds;
  int _cloudsSize;
  std::vector<std::string> _errors;



protected:
  void onChangedContext();

public:

  PointCloudsRenderer() :
  _cloudsSize(0)
  {
  }

  ~PointCloudsRenderer();

  RenderState getRenderState(const G3MRenderContext* rc);

  void render(const G3MRenderContext* rc,
              GLState* glState);

  void onResizeViewportEvent(const G3MEventContext* ec,
                             int width, int height);

  void addPointCloud(const URL& serverURL,
                     const std::string& cloudName,
                     PointCloudMetadataListener* metadataListener,
                     bool deleteListener);

  void addPointCloud(const URL& serverURL,
                     const std::string& cloudName,
                     long long downloadPriority,
                     const TimeInterval& timeToCache,
                     bool readExpired,
                     PointCloudMetadataListener* metadataListener,
                     bool deleteListener);
  
  void removeAllPointClouds();
  
};

#endif